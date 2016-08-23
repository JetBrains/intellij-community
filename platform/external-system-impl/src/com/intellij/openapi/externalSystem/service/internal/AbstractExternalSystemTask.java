package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.service.execution.NotSupportedException;
import com.intellij.openapi.externalSystem.service.notification.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates particular task performed by external system integration.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since 1/24/12 7:03 AM
 */
public abstract class AbstractExternalSystemTask implements ExternalSystemTask {

  private static final Logger LOG = Logger.getInstance("#" + AbstractExternalSystemTask.class.getName());

  private final AtomicReference<ExternalSystemTaskState> myState =
    new AtomicReference<>(ExternalSystemTaskState.NOT_STARTED);
  private final AtomicReference<Throwable> myError = new AtomicReference<>();

  @NotNull private final transient Project myIdeProject;

  @NotNull private final ExternalSystemTaskId myId;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private final String myExternalProjectPath;

  protected AbstractExternalSystemTask(@NotNull ProjectSystemId id,
                                       @NotNull ExternalSystemTaskType type,
                                       @NotNull Project project,
                                       @NotNull String externalProjectPath) {
    myExternalSystemId = id;
    myIdeProject = project;
    myId = ExternalSystemTaskId.create(id, type, myIdeProject);
    myExternalProjectPath = externalProjectPath;
  }

  @NotNull
  public ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @NotNull
  public ExternalSystemTaskId getId() {
    return myId;
  }

  @NotNull
  public ExternalSystemTaskState getState() {
    return myState.get();
  }

  protected void setState(@NotNull ExternalSystemTaskState state) {
    myState.set(state);
  }

  protected boolean compareAndSetState(@NotNull ExternalSystemTaskState expect, @NotNull ExternalSystemTaskState update) {
    return myState.compareAndSet(expect, update);
  }

  @Override
  public Throwable getError() {
    return myError.get();
  }

  @NotNull
  public Project getIdeProject() {
    return myIdeProject;
  }

  @NotNull
  public String getExternalProjectPath() {
    return myExternalProjectPath;
  }

  public void refreshState() {
    if (getState() != ExternalSystemTaskState.IN_PROGRESS) {
      return;
    }
    final ExternalSystemFacadeManager manager = ServiceManager.getService(ExternalSystemFacadeManager.class);
    try {
      final RemoteExternalSystemFacade facade = manager.getFacade(myIdeProject, myExternalProjectPath, myExternalSystemId);
      setState(facade.isTaskInProgress(getId()) ? ExternalSystemTaskState.IN_PROGRESS : ExternalSystemTaskState.FAILED);
    }
    catch (Throwable e) {
      setState(ExternalSystemTaskState.FAILED);
      myError.set(e);
      if (!myIdeProject.isDisposed()) {
        LOG.warn(e);
      }
    }
  }

  @Override
  public void execute(@NotNull final ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners) {
    indicator.setIndeterminate(true);
    ExternalSystemTaskNotificationListenerAdapter adapter = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        indicator.setText(wrapProgressText(event.getDescription()));
      }
    };
    final ExternalSystemTaskNotificationListener[] ls;
    if (listeners.length > 0) {
      ls = ArrayUtil.append(listeners, adapter);
    }
    else {
      ls = new ExternalSystemTaskNotificationListener[]{adapter};
    }

    execute(ls);
  }

  @Override
  public void execute(@NotNull ExternalSystemTaskNotificationListener... listeners) {
    if (!compareAndSetState(ExternalSystemTaskState.NOT_STARTED, ExternalSystemTaskState.IN_PROGRESS)) return;

    ExternalSystemProgressNotificationManager progressManager = ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    for (ExternalSystemTaskNotificationListener listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }
    ExternalSystemProcessingManager processingManager = ServiceManager.getService(ExternalSystemProcessingManager.class);
    try {
      processingManager.add(this);
      doExecute();
      setState(ExternalSystemTaskState.FINISHED);
    }
    catch (Throwable e) {
      setState(ExternalSystemTaskState.FAILED);
      myError.set(e);
      LOG.warn(e);
    }
    finally {
      for (ExternalSystemTaskNotificationListener listener : listeners) {
        progressManager.removeNotificationListener(listener);
      }
      processingManager.release(getId());
    }
  }

  protected abstract void doExecute() throws Exception;

  @Override
  public boolean cancel(@NotNull final ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners) {
    indicator.setIndeterminate(true);
    ExternalSystemTaskNotificationListenerAdapter adapter = new ExternalSystemTaskNotificationListenerAdapter() {
      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        indicator.setText(wrapProgressText(event.getDescription()));
      }
    };
    final ExternalSystemTaskNotificationListener[] ls;
    if (listeners.length > 0) {
      ls = ArrayUtil.append(listeners, adapter);
    }
    else {
      ls = new ExternalSystemTaskNotificationListener[]{adapter};
    }

    return cancel(ls);
  }

  @Override
  public boolean cancel(@NotNull ExternalSystemTaskNotificationListener... listeners) {
    ExternalSystemTaskState currentTaskState = getState();
    if (currentTaskState.isStopped()) return true;

    ExternalSystemProgressNotificationManager progressManager = ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    for (ExternalSystemTaskNotificationListener listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }

    if (!compareAndSetState(currentTaskState, ExternalSystemTaskState.CANCELING)) return false;

    boolean result = false;
    try {
      result = doCancel();
      setState(result ? ExternalSystemTaskState.CANCELED : ExternalSystemTaskState.CANCELLATION_FAILED);
      return result;
    }
    catch (NotSupportedException e) {
      NotificationData notification =
        new NotificationData("Cancellation failed", e.getMessage(), NotificationCategory.WARNING, NotificationSource.PROJECT_SYNC);
      notification.setBalloonNotification(true);
      ExternalSystemNotificationManager.getInstance(getIdeProject()).showNotification(getExternalSystemId(), notification);
    }
    catch (Throwable e) {
      setState(ExternalSystemTaskState.CANCELLATION_FAILED);
      myError.set(e);
      LOG.warn(e);
    }
    finally {
      for (ExternalSystemTaskNotificationListener listener : listeners) {
        progressManager.removeNotificationListener(listener);
      }
    }
    return result;
  }

  protected abstract boolean doCancel() throws Exception;


  @NotNull
  protected String wrapProgressText(@NotNull String text) {
    return ExternalSystemBundle.message("progress.update.text", getExternalSystemId(), text);
  }

  @Override
  public int hashCode() {
    return myId.hashCode() + myExternalSystemId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractExternalSystemTask task = (AbstractExternalSystemTask)o;
    return myId.equals(task.myId) && myExternalSystemId.equals(task.myExternalSystemId);
  }

  @Override
  public String toString() {
    return String.format("%s task %s: %s", myExternalSystemId.getReadableName(), myId, myState);
  }
}
