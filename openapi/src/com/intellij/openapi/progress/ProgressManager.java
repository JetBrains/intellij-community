/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class ProgressManager {
  private static volatile ProgressManager ourCachedInstance = null;
  public static ProgressManager getInstance() {
    ProgressManager instance = ourCachedInstance;
    if( instance == null )
      instance = ourCachedInstance = ApplicationManager.getApplication().getComponent(ProgressManager.class);
    return instance;
  }

  public abstract boolean hasProgressIndicator();
  public abstract boolean hasModalProgressIndicator();

  public abstract void runProcess(Runnable process, ProgressIndicator progress) throws ProcessCanceledException;

  public abstract ProgressIndicator getProgressIndicator();

  public abstract void checkCanceled();

  public abstract void registerFunComponentProvider(ProgressFunComponentProvider provider);
  public abstract void removeFunComponentProvider(ProgressFunComponentProvider provider);
  public abstract JComponent getProvidedFunComponent(Project project, @NonNls String processId);

  public abstract void setCancelButtonText(String cancelButtonText);


  /**
   * Runs the specified operation in a background thread and shows a modal progress dialog in the
   * main thread while the operation is executing.
   *
   * @param process       the operation to execute.
   * @param progressTitle the title of the progress window.
   * @param canBeCanceled whether "Cancel" button is shown on the progress window.
   * @param project       the project in the context of which the operation is executed.
   * @return true if the operation completed successfully, false if it was cancelled.
   */
  public abstract boolean runProcessWithProgressSynchronously(Runnable process,
                                                              String progressTitle,
                                                              boolean canBeCanceled,
                                                              Project project);
}
