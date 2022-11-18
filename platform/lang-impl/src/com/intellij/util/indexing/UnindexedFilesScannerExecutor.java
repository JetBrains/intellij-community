// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbModeTask;
import com.intellij.openapi.project.MergingQueueGuiExecutor;
import com.intellij.openapi.project.MergingTaskQueue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

@Service(Service.Level.PROJECT)
public final class UnindexedFilesScannerExecutor extends MergingQueueGuiExecutor<UnindexedFilesScanner> implements Disposable {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesScannerExecutor.class);
  private final AtomicReference<ProgressIndicator> runningTask = new AtomicReference<>();

  private static class TaskQueueListener implements MergingQueueGuiExecutor.DumbTaskListener {
    private final Project project;

    private TaskQueueListener(Project project) { this.project = project; }

    @Override
    public boolean beforeFirstTask() {
      return true;
    }

    @Override
    public void afterLastTask() {
      UnindexedFilesScannerExecutor executor = project.getService(UnindexedFilesScannerExecutor.class);
      if (!executor.getTaskQueue().isEmpty()) {
        // process the tasks which were submitted after background thread finished polling, but before is set "completed" flag
        executor.startBackgroundProcess();
      }
    }
  }

  public UnindexedFilesScannerExecutor(Project project) {
    super(project, new MergingTaskQueue<>(), new TaskQueueListener(project));
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

  private void startTaskInSmartMode(UnindexedFilesScanner task) {
    getTaskQueue().addTask(task);
    startBackgroundProcess();
  }

  private void startTaskInDumbMode(UnindexedFilesScanner task) {
    DumbModeTask dumbTask = new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        ProgressIndicator old = runningTask.getAndSet(indicator);
        try {
          LOG.assertTrue(old == null, "Old = " + old);
          task.perform(indicator);
        }
        finally {
          old = runningTask.getAndSet(null);
          LOG.assertTrue(old == indicator, "Old = " + old);
        }
      }
    };
    Disposer.register(dumbTask, task);
    dumbTask.queue(getProject());
  }

  @Override
  protected void runSingleTask(MergingTaskQueue.@NotNull QueuedTask<UnindexedFilesScanner> task) {
    ProgressIndicator indicator = task.getIndicator();
    ProgressIndicator old = runningTask.getAndSet(indicator);
    try {
      LOG.assertTrue(old == null, "Old = " + old);
      runningTask.set(indicator);
      super.runSingleTask(task);
    }
    finally {
      old = runningTask.getAndSet(null);
      LOG.assertTrue(old == indicator, "Old = " + old);
    }
  }

  private void cancelRunningTask() {
    ProgressIndicator indicator = runningTask.get();
    if (indicator != null) indicator.cancel();
  }

  @Override
  public void dispose() {
    getTaskQueue().disposePendingTasks();
  }
}
