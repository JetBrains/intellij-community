// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.PingProgress;
import com.intellij.openapi.project.*;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

@Service(Service.Level.PROJECT)
public final class UnindexedFilesScannerExecutor extends MergingQueueGuiExecutor<UnindexedFilesScanner> implements Disposable {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesScannerExecutor.class);
  private final AtomicReference<ProgressIndicator> runningTask = new AtomicReference<>();

  @NotNull
  public static UnindexedFilesScannerExecutor getInstance(@NotNull Project project) {
    return project.getService(UnindexedFilesScannerExecutor.class);
  }

  private static class TaskQueueListener implements ExecutorStateListener {
    private final FilesScanningListener projectLevelEventPublisher;

    private TaskQueueListener(Project project) {
      this.projectLevelEventPublisher = project.getMessageBus().syncPublisher(FilesScanningListener.TOPIC);
    }

    @Override
    public boolean beforeFirstTask() {
      projectLevelEventPublisher.filesScanningStarted();
      return true;
    }

    @Override
    public void afterLastTask() {
      projectLevelEventPublisher.filesScanningFinished();
    }
  }

  public UnindexedFilesScannerExecutor(Project project) {
    super(project, new MergingTaskQueue<>(), new TaskQueueListener(project),
          IndexingBundle.message("progress.indexing.scanning"), IndexingBundle.message("progress.indexing.scanning.paused"));
  }

  public void submitTask(UnindexedFilesScanner task) {
    // Two tasks with limited checks should be just run one after another.
    // A case of a full check followed by a limited change cancelling first one and making a full check anew results
    // in endless restart of full checks on Windows with empty Maven cache.
    // So only in case the second one is a full check should the first one be cancelled.
    if (task.isFullIndexUpdate()) {
      // we don't want to execute any of the existing tasks - the only task we want to execute will be submitted few lines below
      getTaskQueue().cancelAllTasks();
      cancelRunningTask();
    }

    if (UnindexedFilesScanner.shouldScanInSmartMode()) {
      startTaskInSmartMode(task);
    }
    else {
      startTaskInDumbMode(task);
    }
  }

  private void startTaskInSmartMode(@NotNull UnindexedFilesScanner task) {
    getTaskQueue().addTask(task);
    startBackgroundProcess();
  }

  private void startTaskInDumbMode(@NotNull UnindexedFilesScanner task) {
    wrapAsDumbTask(task).queue(getProject());
  }

  @NotNull
  @VisibleForTesting
  DumbModeTask wrapAsDumbTask(@NotNull UnindexedFilesScanner task) {
    return new UnindexedFilesScannerAsDumbModeTaskWrapper(task, runningTask);
  }

  @Override
  protected void runSingleTask(MergingTaskQueue.@NotNull QueuedTask<UnindexedFilesScanner> task, @Nullable StructuredIdeActivity activity) {
    ProgressIndicator indicator = task.getIndicator();
    ProgressIndicator old = runningTask.getAndSet(indicator);
    try {
      LOG.assertTrue(old == null, "Old = " + old);
      runningTask.set(indicator);
      super.runSingleTask(task, activity);
    }
    finally {
      old = runningTask.getAndSet(null);
      LOG.assertTrue(old == indicator, "Old = " + old);
    }
  }

  private void cancelRunningTask() {
    ProgressIndicator indicator = runningTask.get();
    if (indicator != null) {
      indicator.cancel();
      ProgressSuspender suspender = ProgressSuspender.getSuspender(indicator);
      if (suspender != null && suspender.isSuspended()) {
        suspender.resumeProcess();
      }
    }
  }

  public void cancelAllTasksAndWait() {
    getTaskQueue().cancelAllTasks();
    cancelRunningTask();

    while (isRunning()) {
      PingProgress.interactWithEdtProgress();
      LockSupport.parkNanos(50_000_000);
    }
  }

  @Override
  public void dispose() {
    getTaskQueue().disposePendingTasks();
  }

  /**
   * This method does not have "happens before" semantics. It requests GUI suspender to suspend and executes runnable without waiting for
   * all the running tasks to pause.
   */
  public void suspendScanningAndIndexingThenRun(@NotNull @NlsContexts.ProgressText String activityName, @NotNull Runnable runnable) {
    suspendAndRun(activityName, () -> {
      DumbService.getInstance(getProject()).suspendIndexingAndRun(activityName, runnable);
    });
  }
}
