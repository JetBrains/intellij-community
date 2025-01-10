// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.service.ExternalSystemFacadeManager;
import com.intellij.openapi.externalSystem.service.ExternalSystemTaskProgressIndicatorUpdater;
import com.intellij.openapi.externalSystem.service.execution.NotSupportedException;
import com.intellij.openapi.externalSystem.service.notification.*;
import com.intellij.openapi.externalSystem.service.remote.ExternalSystemProgressNotificationManagerImpl;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Encapsulates particular task performed by external system integration.
 * <p/>
 * Thread-safe.
 */
public abstract class AbstractExternalSystemTask extends UserDataHolderBase implements ExternalSystemTask {

  private static final Logger LOG = Logger.getInstance(AbstractExternalSystemTask.class);

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

  @Override
  @NotNull
  public ExternalSystemTaskId getId() {
    return myId;
  }

  @Override
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

  @Override
  public void refreshState() {
    if (getState() != ExternalSystemTaskState.IN_PROGRESS) {
      return;
    }
    try {
      var manager = ExternalSystemFacadeManager.getInstance();
      var facade = manager.getFacade(myIdeProject, myExternalProjectPath, myExternalSystemId);
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

  protected abstract void doExecute() throws Exception;

  protected abstract boolean doCancel() throws Exception;

  @Override
  public void execute(@NotNull final ProgressIndicator indicator, ExternalSystemTaskNotificationListener @NotNull ... listeners) {
    indicator.setIndeterminate(true);
    var listener = getProgressIndicatorListener(indicator);
    execute(ArrayUtil.append(listeners, listener));
  }

  @Override
  public boolean cancel(@NotNull final ProgressIndicator indicator, ExternalSystemTaskNotificationListener @NotNull ... listeners) {
    indicator.setIndeterminate(true);
    var listener = getProgressIndicatorListener(indicator);
    return cancel(ArrayUtil.append(listeners, listener));
  }

  @Override
  public void execute(ExternalSystemTaskNotificationListener @NotNull ... listeners) {
    if (!compareAndSetState(ExternalSystemTaskState.NOT_STARTED, ExternalSystemTaskState.IN_PROGRESS)) {
      return;
    }
    try {
      addProgressListeners(listeners);
      withProcessingManager(() -> {
        withExecutionProgressManager(() -> {
          doExecute();
        });
      });
      setState(ExternalSystemTaskState.FINISHED);
    }
    catch (Exception e) {
      LOG.debug(e);
      myError.set(e);
      setState(ExternalSystemTaskState.FAILED);
    }
    catch (Throwable e) {
      LOG.error(e);
      myError.set(e);
      setState(ExternalSystemTaskState.FAILED);
    }
  }

  @Override
  public boolean cancel(ExternalSystemTaskNotificationListener @NotNull ... listeners) {
    var currentTaskState = getState();
    if (currentTaskState.isStopped()) {
      return true;
    }
    if (!compareAndSetState(currentTaskState, ExternalSystemTaskState.CANCELING)) {
      return false;
    }
    try {
      addProgressListeners(listeners);
      return withCancellationProgressManager(() -> {
        return doCancel();
      });
    }
    catch (NotSupportedException e) {
      setState(ExternalSystemTaskState.CANCELLATION_FAILED);
      showCancellationFailedNotification(e);
    }
    catch (Throwable e) {
      setState(ExternalSystemTaskState.CANCELLATION_FAILED);
      myError.set(e);
      LOG.warn(e);
    }
    return false;
  }

  private void showCancellationFailedNotification(@NotNull NotSupportedException exception) {
    var notification = new NotificationData(
      ExternalSystemBundle.message("progress.cancel.failed"),
      exception.getMessage(),
      NotificationCategory.WARNING,
      NotificationSource.PROJECT_SYNC
    );
    notification.setBalloonNotification(true);
    var notificationManager = ExternalSystemNotificationManager.getInstance(getIdeProject());
    notificationManager.showNotification(getExternalSystemId(), notification);
  }

  private void addProgressListeners(ExternalSystemTaskNotificationListener @NotNull ... listeners) {
    var progressManager = ExternalSystemProgressNotificationManager.getInstance();
    for (var listener : listeners) {
      progressManager.addNotificationListener(getId(), listener);
    }
  }

  private void withProcessingManager(@NotNull Runnable runnable) {
    var processingManager = ExternalSystemProcessingManager.getInstance();
    try {
      processingManager.add(this);
      runnable.run();
    }
    finally {
      processingManager.release(getId());
    }
  }

  private void withExecutionProgressManager(@NotNull ThrowableRunnable<Exception> runnable) {
    var projectPath = myExternalProjectPath;
    var id = myId;

    var progressManager = ExternalSystemProgressNotificationManagerImpl.getInstanceImpl();
    try {
      progressManager.onStart(projectPath, id);
      runnable.run();
      progressManager.onSuccess(projectPath, id);
    }
    catch (ProcessCanceledException exception) {
      progressManager.onCancel(projectPath, id);

      Throwable cause = exception.getCause();
      if (cause == null || cause instanceof ExternalSystemException) {
        throw exception;
      }
      throw new ProcessCanceledException(new ExternalSystemException(cause));
    }
    catch (ExternalSystemException exception) {
      progressManager.onFailure(projectPath, id, exception);
      throw exception;
    }
    catch (Exception exception) {
      progressManager.onFailure(projectPath, id, exception);
      throw new ExternalSystemException(exception);
    }
    catch (Throwable throwable) {
      var exception = new ExternalSystemException(throwable);
      progressManager.onFailure(projectPath, id, exception);
      throw exception;
    }
    finally {
      progressManager.onEnd(projectPath, id);
    }
  }

  private <R> R withCancellationProgressManager(@NotNull ThrowableComputable<R, Exception> runnable) throws Exception {
    var progressManager = ExternalSystemProgressNotificationManagerImpl.getInstanceImpl();
    try {
      progressManager.beforeCancel(getId());
      return runnable.compute();
    }
    finally {
      progressManager.onCancel(myExternalProjectPath, getId());
    }
  }

  @NotNull
  protected @NlsContexts.ProgressText String wrapProgressText(@NotNull String text) {
    return ExternalSystemBundle.message("progress.update.text", getExternalSystemId().getReadableName(), text);
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

  /**
   * @see com.intellij.openapi.util.UserDataHolderBase#copyUserDataTo
   */
  @ApiStatus.Internal
  @SuppressWarnings({"unchecked", "rawtypes"})
  protected void putUserDataTo(@NotNull UserDataHolder dataHolder) {
    var userMap = getUserMap();
    for (Key key : userMap.getKeys()) {
      dataHolder.putUserData(key, userMap.get(key));
    }
  }

  @ApiStatus.Internal
  protected static @NotNull ExternalSystemTaskNotificationListener wrapWithListener(
    @NotNull ExternalSystemProgressNotificationManagerImpl manager
  ) {
    return new ExternalSystemTaskNotificationListener() {
      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        manager.onStatusChange(event);
      }

      @Override
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
        manager.onTaskOutput(id, text, stdOut);
      }
    };
  }

  private @NotNull ExternalSystemTaskNotificationListener getProgressIndicatorListener(@NotNull ProgressIndicator indicator) {
    var updater = ExternalSystemTaskProgressIndicatorUpdater.getInstanceOrDefault(myExternalSystemId);
    return new ExternalSystemTaskNotificationListener() {
      @Override
      public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
        updater.updateIndicator(event, indicator, text -> wrapProgressText(text));
      }

      @Override
      public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
        updater.onTaskEnd(id);
      }
    };
  }
}
