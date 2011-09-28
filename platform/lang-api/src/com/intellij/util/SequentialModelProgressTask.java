package com.intellij.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;

/**
 * Allows to execute {@link SequentialTask} under modal progress.
 * 
 * @author Denis Zhdanov
 * @since 9/27/11 2:52 PM
 */
public class SequentialModelProgressTask extends Task.Modal {

  private static final Logger LOG = Logger.getInstance("#" + SequentialModelProgressTask.class.getName());
  
  /**
   * We want to perform formatting by big chunks at EDT. However, there is a possible case that particular formatting iteration
   * is executed in short amount of time. Hence, we may want to execute more than one formatting action during single EDT iteration.
   * Current constant holds min amount of time to spend to formatting.
   */
  private static final long ITERATION_MIN_TIMES_MILLIS = 500;

  private final String myTitle;

  private ProgressIndicator myIndicator;
  private SequentialTask myTask;

  public SequentialModelProgressTask(@Nullable Project project, @NotNull String title) {
    super(project, title, true);
    myTitle = title;
  }

  @Override
  public void run(@NotNull ProgressIndicator indicator) {
    try {
      doRun(indicator);
    }
    catch (Exception e) {
      LOG.info("Unexpected exception occurred during processing sequential task '" + myTitle + "'", e);
    }
    finally {
      indicator.stop();
    }
  }

  public void doRun(@NotNull ProgressIndicator indicator) throws InvocationTargetException, InterruptedException {
    final SequentialTask task = myTask;
    if (task == null) {
      return;
    }
    
    myIndicator = indicator;
    indicator.setIndeterminate(false);
    prepare(task);
    
    // We need to sync background thread and EDT here in order to avoid situation when event queue is full of processing requests.
    while (!task.isDone()) {
      if (indicator.isCanceled()) {
        task.stop();
        break;
      }
      UIUtil.invokeAndWaitIfNeeded(new Runnable() {
        @Override
        public void run() {
          long start = System.currentTimeMillis();
          try {
            while (!task.isDone() && System.currentTimeMillis() - start < ITERATION_MIN_TIMES_MILLIS) {
              task.iteration();
            }
          }
          catch (RuntimeException e) {
            task.stop();
            throw e;
          }
        }
      });
    }
  }

  public void setTask(@Nullable SequentialTask task) {
    myTask = task;
  }
  
  @Nullable
  public ProgressIndicator getIndicator() {
    return myIndicator;
  }

  /**
   * Executes preliminary jobs prior to the target sequential task processing ({@link SequentialTask#prepare()} by default).
   * 
   * @param task  task to be executed
   */
  protected void prepare(@NotNull SequentialTask task) {
    task.prepare();
  }
}
