/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.progress;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author yole
 */
@SomeQueue
public class BackgroundTaskQueue {
  private final Project myProject;
  private final Queue<Task> myQueue = new LinkedList<Task>();
  private boolean myHasActiveTask = false;
  private Task.Backgroundable myRunnerTask;
  private Boolean myForcedTestMode = null;

  public BackgroundTaskQueue(String title) {
    this(null, title);
  }

  public BackgroundTaskQueue(Project project, String title) {
    myProject = project;
    myRunnerTask = new Task.Backgroundable(project, title) {
      public void run(@NotNull final ProgressIndicator indicator) {
        while (true) {
          final Task task;

          synchronized(myQueue) {
            myHasActiveTask = true;
            task = myQueue.poll();
            if (task == null) {
              myHasActiveTask = false;
              return;
            }
          }

          indicator.setText(task.getTitle());
          try {
            task.run(indicator);
          } catch (ProcessCanceledException e) {
            //ok
          }

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              if (myProject == null || !myProject.isDisposed()) {
                task.onSuccess();
              }
            }
          }, ModalityState.NON_MODAL);
        }
      }
    };
  }

  public void run(Task.Backgroundable task) {
    if (isInTestMode()) {
      task.run(new EmptyProgressIndicator());
      task.onSuccess();
    }
    else if (task.isConditionalModal() && !task.shouldStartInBackground()) {
      ProgressManager.getInstance().run(task);
    }
    else {
      boolean hadActiveTask;
      synchronized (myQueue) {
        hadActiveTask = myHasActiveTask;
        myQueue.offer(task);
        myHasActiveTask = true;
      }
      if (! hadActiveTask) {
        runRunner();
      }
    }
  }


  private void runRunner() {
    final Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode()) {
      application.executeOnPooledThread(new Runnable() {
        public void run() {
          myRunnerTask.run(new EmptyProgressIndicator());
        }
      });
    } else {
      if (application.isDispatchThread()) {
        ProgressManager.getInstance().run(myRunnerTask);
      }
      else {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            if (myProject == null || !myProject.isDisposed()) {
              ProgressManager.getInstance().run(myRunnerTask);
            }
          }
        });
      }
    }
  }

  private boolean isInTestMode() {
    if (myForcedTestMode != null) return myForcedTestMode.booleanValue();
    return ApplicationManager.getApplication().isUnitTestMode();
  }

  public void setTestMode(boolean forcedTestMode) {
    myForcedTestMode = forcedTestMode;
  }
}
