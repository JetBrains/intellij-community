package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * Provides gradle tasks monitoring and management facilities.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/8/12 1:52 PM
 */
public class ExternalSystemProcessingManager implements ExternalSystemTaskNotificationListener, Disposable {

  /**
   * We receive information about the tasks being enqueued to the slave processes which work directly with external systems here.
   * However, there is a possible situation when particular task has been sent to execution but remote side has not been responding
   * for a while. There at least two possible explanations then:
   * <pre>
   * <ul>
   *   <li>the task is still in progress (e.g. great number of libraries is being downloaded);</li>
   *   <li>remote side has fallen (uncaught exception; manual slave process kill etc);</li>
   * </ul>
   * </pre>
   * We need to distinguish between them, so, we perform 'task pings' if any task is executed too long. Current constant holds
   * criteria of 'too long execution'.
   */
  private static final long TOO_LONG_EXECUTION_MS = TimeUnit.SECONDS.toMillis(10);

  @NotNull private final ConcurrentMap<ExternalSystemTaskId, Long> myTasksInProgress = ContainerUtil.newConcurrentMap();
  @NotNull private final Alarm                                     myAlarm           = new Alarm(Alarm.ThreadToUse.SHARED_THREAD);

  @NotNull private final ExternalSystemFacadeManager               myFacadeManager;
  @NotNull private final ExternalSystemProgressNotificationManager myProgressNotificationManager;

  public ExternalSystemProcessingManager(@NotNull ExternalSystemFacadeManager facadeManager,
                                         @NotNull ExternalSystemProgressNotificationManager notificationManager)
  {
    myFacadeManager = facadeManager;
    myProgressNotificationManager = notificationManager;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    notificationManager.addNotificationListener(this);
  }

  @Override
  public void dispose() {
    myProgressNotificationManager.removeNotificationListener(this);
    myAlarm.cancelAllRequests();
  }

  /**
   * Allows to check if any task of the given type is being executed at the moment.  
   *
   * @param type  target task type
   * @return      <code>true</code> if any task of the given type is being executed at the moment;
   *              <code>false</code> otherwise
   */
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
  public void onQueued(@NotNull ExternalSystemTaskId id) {
    myTasksInProgress.put(id, System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
    if (myAlarm.getActiveRequestCount() <= 0) {
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          update();
        }
      }, TOO_LONG_EXECUTION_MS);
    }
  }

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id) {
    myTasksInProgress.put(id, System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
  }

  @Override
  public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
    myTasksInProgress.put(event.getId(), System.currentTimeMillis() + TOO_LONG_EXECUTION_MS); 
  }

  @Override
  public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
    myTasksInProgress.put(id, System.currentTimeMillis() + TOO_LONG_EXECUTION_MS);
  }

  @Override
  public void onEnd(@NotNull ExternalSystemTaskId id) {
    myTasksInProgress.remove(id);
    if (myTasksInProgress.isEmpty()) {
      myAlarm.cancelAllRequests();
    }
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
  }

  @Override
  public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
  }

  public void update() {
    long delay = TOO_LONG_EXECUTION_MS;
    Map<ExternalSystemTaskId, Long> newState = ContainerUtilRt.newHashMap();

    Map<ExternalSystemTaskId, Long> currentState = ContainerUtilRt.newHashMap(myTasksInProgress);
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
      myAlarm.addRequest(new Runnable() {
        @Override
        public void run() {
          update(); 
        }
      }, delay);
    }
  }
}
