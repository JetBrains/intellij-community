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
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@SomeQueue
public class ProgressManagerQueue extends AbstractTaskQueue<Runnable> {
  private final ProgressManager myProgressManager;
  private final Task.Backgroundable myTask;

  public ProgressManagerQueue(final Project project, final String title) {
    myProgressManager = ProgressManager.getInstance();
    myTask = new Task.Backgroundable(project, title) {
      public void run(@NotNull ProgressIndicator indicator) {
        myQueueWorker.run();
      }
    };
  }

  protected void runMe() {
    final Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      myProgressManager.run(myTask);
    }
    else {
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
