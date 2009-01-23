package com.intellij.openapi.progress;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class ProgressManagerQueue extends AbstractTaskQueue<Runnable> {
  private final ProgressManager myProgressManager;
  private final Task.Backgroundable myTask;

  public ProgressManagerQueue(final Project project, final String title) {
    myProgressManager = ProgressManager.getInstance();
    myTask = new Task.Backgroundable(project, title) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        myQueueWorker.run();
      }
    };
  }

  protected void runMe() {
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      myProgressManager.run(myTask);
    } else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          myProgressManager.run(myTask);
        }
      });
    }
  }

  protected void runStuff(final Runnable stuff) {
    try {
      stuff.run();
    }
    catch (ProcessCanceledException e) {
      //
    }
  }
}
