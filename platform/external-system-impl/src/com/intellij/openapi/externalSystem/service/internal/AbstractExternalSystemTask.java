package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.RemoteExternalSystemFacade;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager;
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
    new AtomicReference<ExternalSystemTaskState>(ExternalSystemTaskState.NOT_STARTED);
  private final AtomicReference<Throwable>               myError = new AtomicReference<Throwable>();

  @NotNull private final transient Project myIdeProject;

  @NotNull private final ExternalSystemTaskId myId;
  @NotNull private final ProjectSystemId      myExternalSystemId;
  @NotNull private final String               myExternalProjectPath;

  protected AbstractExternalSystemTask(@NotNull ProjectSystemId id,
                                       @NotNull ExternalSystemTaskType type,
                                       @NotNull Project project,
                                       @NotNull String externalProjectPath)
  {
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
      ls = new ExternalSystemTaskNotificationListener[] { adapter };
    }

    execute(ls);
  }

  @Override
  public void execute(@NotNull ExternalSystemTaskNotificationListener... listeners) {
    ExternalSystemProgressNotificationManager progressManager = ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    for (ExternalSystemTaskNotificationListener listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }
    try {
      doExecute();
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
    }
  }

  protected abstract void doExecute() throws Exception;

  @Override
  public void cancel(@NotNull final ProgressIndicator indicator, @NotNull ExternalSystemTaskNotificationListener... listeners) {
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
      ls = new ExternalSystemTaskNotificationListener[] { adapter };
    }

    cancel(ls);
  }

  @Override
  public void cancel(@NotNull ExternalSystemTaskNotificationListener... listeners) {
    ExternalSystemProgressNotificationManager progressManager = ServiceManager.getService(ExternalSystemProgressNotificationManager.class);
    for (ExternalSystemTaskNotificationListener listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }
    try {
      doCancel();
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
    }
  }

  protected abstract void doCancel() throws Exception;


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
