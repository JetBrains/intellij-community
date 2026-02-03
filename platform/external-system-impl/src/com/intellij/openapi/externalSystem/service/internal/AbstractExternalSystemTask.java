// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.execution.process.ProcessOutputType;
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

import java.util.concurrent.CancellationException;
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

  private final transient @NotNull Project myIdeProject;

  private final @NotNull ExternalSystemTaskId myId;
  private final @NotNull ProjectSystemId myExternalSystemId;
  private final @NotNull String myExternalProjectPath;

  protected AbstractExternalSystemTask(@NotNull ProjectSystemId id,
                                       @NotNull ExternalSystemTaskType type,
                                       @NotNull Project project,
                                       @NotNull String externalProjectPath) {
    myExternalSystemId = id;
    myIdeProject = project;
    myId = ExternalSystemTaskId.create(id, type, myIdeProject);
    myExternalProjectPath = externalProjectPath;
  }

  public @NotNull ProjectSystemId getExternalSystemId() {
    return myExternalSystemId;
  }

  @Override
  public @NotNull ExternalSystemTaskId getId() {
    return myId;
  }

  @Override
  public @NotNull ExternalSystemTaskState getState() {
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

  public @NotNull Project getIdeProject() {
    return myIdeProject;
  }

  public @NotNull String getExternalProjectPath() {
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
  public void execute(final @NotNull ProgressIndicator indicator, ExternalSystemTaskNotificationListener @NotNull ... listeners) {
    indicator.setIndeterminate(true);
    var listener = getProgressIndicatorListener(indicator);
    execute(ArrayUtil.append(listeners, listener));
  }

  @Override
  public boolean cancel(final @NotNull ProgressIndicator indicator, ExternalSystemTaskNotificationListener @NotNull ... listeners) {
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
          withExecutionState(() -> {
            doExecute();
          });
        });
      });
    }
    catch (CancellationException __) {
      // the exception shouldn't be thrown due to the legacy architecture decision
      // if the exception would be thrown, the cancellation will never be handled due to
      // {@link com.intellij.openapi.externalSystem.util.ExternalSystemUtil.handleSyncResult}
      LOG.info(String.format("The execution %s was cancelled", myId));
    }
    catch (Exception e) {
      LOG.warn(myId + ": Task execution failed", e);
    }
    catch (Throwable e) {
      LOG.error(e);
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
        return withCancellationState(() -> {
          return doCancel();
        });
      });
    }
    catch (NotSupportedException e) {
      showCancellationFailedNotification(e);
    }
    catch (Exception e) {
      LOG.debug(e);
    }
    catch (Throwable e) {
      LOG.error(e);
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

  private boolean withCancellationProgressManager(@NotNull ThrowableComputable<Boolean, Exception> runnable) throws Exception {
    var progressManager = ExternalSystemProgressNotificationManagerImpl.getInstanceImpl();
    try {
      progressManager.beforeCancel(getId());
      return runnable.compute();
    }
    finally {
      progressManager.onCancel(myExternalProjectPath, getId());
    }
  }

  private void withExecutionState(@NotNull ThrowableRunnable<Exception> runnable) throws Exception {
    try {
      runnable.run();
      setState(ExternalSystemTaskState.FINISHED);
    }
    catch (ProcessCanceledException exception) {
      setState(ExternalSystemTaskState.CANCELED);
      myError.set(exception);
      throw exception;
    }
    catch (Throwable exception) {
      setState(ExternalSystemTaskState.FAILED);
      myError.set(exception);
      throw exception;
    }
  }

  private boolean withCancellationState(@NotNull ThrowableComputable<Boolean, Exception> runnable) throws Exception {
    try {
      var isCancelled = runnable.compute();
      if (isCancelled) {
        setState(ExternalSystemTaskState.CANCELED);
      }
      return isCancelled;
    }
    catch (ProcessCanceledException exception) {
      setState(ExternalSystemTaskState.CANCELED);
      myError.set(exception);
      throw exception;
    }
    catch (Throwable exception) {
      setState(ExternalSystemTaskState.CANCELLATION_FAILED);
      myError.set(exception);
      throw exception;
    }
  }

  protected @NotNull @NlsContexts.ProgressText String wrapProgressText(@NotNull String text) {
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
      public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) {
        manager.onTaskOutput(id, text, processOutputType);
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
