package org.camunda.bench.camunda7;

import org.camunda.bpm.client.ExternalTaskClient;

import org.camunda.bpm.engine.variable.Variables;
import org.camunda.bpm.engine.variable.value.ObjectValue;

import org.camunda.bench.core.config.BenchmarkConfiguration;
import org.camunda.bench.core.StatisticsCollector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class JobWorker {

    @Autowired
    private BenchmarkConfiguration config;

    // TODO: Check if we can/need to check if the scheduler can catch up with all its work (or if it is overwhelmed)
    @Autowired
    private TaskScheduler scheduler;

    @Autowired
    private StatisticsCollector stats;

    private void registerWorker(String jobType) {

        // bootstrap the client
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .build();

        // subscribe to the topic
        client.subscribe("creditScoreChecker")
                .lockDuration(1000)
                .handler((externalTask, externalTaskService) -> {

                    // retrieve a variable from the Process Engine
                    int defaultScore = externalTask.getVariable("defaultScore");

                    List<Integer> creditScores = new ArrayList<>(Arrays.asList(defaultScore, 9, 1, 4, 10));

                    // create an object typed variable
                    ObjectValue creditScoresObject = Variables
                            .objectValue(creditScores)
                            .create();

                    // complete the external task
                    externalTaskService.complete(externalTask,
                            Collections.singletonMap("creditScores", creditScoresObject));

                    System.out.println("The External Task " + externalTask.getId() + " has been completed!");

                }).open();

        long fixedBackOffDelay = config.getFixedBackOffDelay();

        JobWorkerBuilderStep1.JobWorkerBuilderStep3 step3 = client.newWorker()
                .jobType(jobType)
                .handler(new SimpleDelayCompletionHandler(false))
                .name(jobType);

        if(fixedBackOffDelay > 0) {
            step3.backoffSupplier(new FixedBackoffSupplier(fixedBackOffDelay));
        }

        step3.open();
    }

}
