// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.ArrayUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@ApiStatus.Internal
public final class ExternalSystemProcessingManagerImpl
  implements ExternalSystemProcessingManager, ExternalSystemTaskNotificationListener, Disposable {

  private static final Logger LOG = Logger.getInstance(ExternalSystemProcessingManagerImpl.class);

  /**
   * We receive information about the tasks being enqueued to the dependent processes which work directly with external systems here.
   * However, there is a possible situation when a particular task has been sent to execution but remote side has not been responding
   * for a while. There are at least two possible explanations then:
   * <pre>
   * <ul>
   *   <li>the task is still in progress (e.g. great number of libraries is being downloaded);</li>
   *   <li>remote side has fallen (uncaught exception; manual dependent process kill etc);</li>
   * </ul>
   * </pre>
   * We need to distinguish between them, so, we perform 'task pings' if any task is executed too long. Current constant holds
   * criteria of 'too long execution'.
   */
  private static final long TOO_LONG_EXECUTION_MS = TimeUnit.SECONDS.toMillis(10);

  private final @NotNull ConcurrentMap<ExternalSystemTaskId, Long> myTasksInProgress = new ConcurrentHashMap<>();
  private final @NotNull ConcurrentMap<ExternalSystemTaskId, ExternalSystemTask> myTasksDetails = new ConcurrentHashMap<>();
  private final @NotNull Alarm myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, this);

  private final @NotNull ExternalSystemFacadeManager myFacadeManager;

  public ExternalSystemProcessingManagerImpl() {
    Application app = ApplicationManager.getApplication();
    myFacadeManager = ExternalSystemFacadeManager.getInstance();
    if (app.isUnitTestMode()) {
      return;
    }
    ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(this, this);
  }

  @Override
  public void dispose() {
    myAlarm.cancelAllRequests();
  }

  @Override
  public boolean hasTaskOfTypeInProgress(@NotNull ExternalSystemTaskType type, @NotNull Project project) {
    String projectId = ExternalSystemTaskId.getProjectId(project);
    for (ExternalSystemTaskId id : myTasksInProgress.keySet()) {
      if (type.equals(id.getType()) && projectId.equals(id.getIdeProjectId())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public @Nullable ExternalSystemTask findTask(@NotNull ExternalSystemTaskId id) {
    return myTasksDetails.get(id);
  }

  @Override
  public @Nullable ExternalSystemTask findTask(@NotNull ExternalSystemTaskType type,
                                               @NotNull ProjectSystemId projectSystemId,
                                               final @NotNull String externalProjectPath) {
    for (ExternalSystemTask task : myTasksDetails.values()) {
      if (task instanceof AbstractExternalSystemTask externalSystemTask) {
        if (externalSystemTask.getId().getType() == type &&
            externalSystemTask.getExternalSystemId().getId().equals(projectSystemId.getId()) &&
            externalSystemTask.getExternalProjectPath().equals(externalProjectPath)) {
          return task;
        }
      }
    }

    return null;
  }

  @Override
  public @NotNull List<ExternalSystemTask> findTasksOfState(
    @NotNull ProjectSystemId projectSystemId,
    ExternalSystemTaskState @NotNull ... taskStates
  ) {
    List<ExternalSystemTask> result = new SmartList<>();
    for (ExternalSystemTask task : myTasksDetails.values()) {
      if (task instanceof AbstractExternalSystemTask externalSystemTask) {
        if (externalSystemTask.getExternalSystemId().getId().equals(projectSystemId.getId()) &&
            ArrayUtil.contains(externalSystemTask.getState(), taskStates)) {
          result.add(task);
        }
      }
    }
    return result;
  }

  @Override
  public void add(@NotNull ExternalSystemTask task) {
    myTasksDetails.put(task.getId(), task);
  }

  @Override
  public void release(@NotNull ExternalSystemTaskId id) {
    myTasksDetails.remove(id);
  }

  @Override
  public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    myTasksInProgress.put(id, System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
    if (myAlarm.isEmpty()) {
      myAlarm.addRequest(() -> update(), TOO_LONG_EXECUTION_MS);
    }
  }

  @Override
  public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
    Long prev = myTasksInProgress.replace(event.getId(), System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
    if (prev == null) {
      LOG.warn("onStatusChange is invoked before onStart or after onEnd (event: %s)".formatted(event));
    }
  }

  @Override
  public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) {
    Long prev = myTasksInProgress.replace(id, System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
    if (prev == null) {
      LOG.warn("onTaskOutput is invoked before onStart or after onEnd (id: %s, outputType: %s, text: %s)".formatted(id, processOutputType.toString(), text));
    }
  }

  @Override
  public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    myTasksInProgress.remove(id);
    if (myTasksInProgress.isEmpty()) {
      myAlarm.cancelAllRequests();
    }
  }

  private void update() {
    long delay = TOO_LONG_EXECUTION_MS;
    Map<ExternalSystemTaskId, Long> newState = new HashMap<>();

    Map<ExternalSystemTaskId, Long> currentState = new HashMap<>(myTasksInProgress);
    if (currentState.isEmpty()) {
      return;
    }

    for (Map.Entry<ExternalSystemTaskId, Long> entry : currentState.entrySet()) {
      long diff = System.currentTimeMillis() - entry.getValue();
      if (diff > 0) {
        delay = Math.min(delay, diff);
        newState.put(entry.getKey(), entry.getValue());
      }
      else {
        // Perform explicit check on whether the task is still alive.
        if (myFacadeManager.isTaskActive(entry.getKey())) {
          newState.put(entry.getKey(), System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
        }
      }
    }

    myTasksInProgress.clear();
    myTasksInProgress.putAll(newState);

    if (!newState.isEmpty()) {
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(() -> update(), delay);
    }
  }
}
