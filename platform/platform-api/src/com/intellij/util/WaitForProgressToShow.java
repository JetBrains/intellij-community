/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class WaitForProgressToShow {
  private WaitForProgressToShow() {
  }

  public static void runOrInvokeAndWaitAboveProgress(final Runnable command) {
    runOrInvokeAndWaitAboveProgress(command, ModalityState.defaultModalityState());
  }

  public static void runOrInvokeAndWaitAboveProgress(final Runnable command, @Nullable final ModalityState modalityState) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      command.run();
    } else {
      final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
      if (pi != null) {
        execute(pi);
        application.invokeAndWait(command, pi.getModalityState());
      } else {
        final ModalityState notNullModalityState = modalityState == null ? ModalityState.NON_MODAL : modalityState;
        application.invokeAndWait(command, notNullModalityState);
      }
    }
  }

  public static void runOrInvokeLaterAboveProgress(final Runnable command, @Nullable final ModalityState modalityState, @NotNull final Project project) {
    final Application application = ApplicationManager.getApplication();
    if (application.isDispatchThread()) {
      command.run();
    } else {
      final ProgressIndicator pi = ProgressManager.getInstance().getProgressIndicator();
      if (pi != null) {
        execute(pi);
        application.invokeLater(command, pi.getModalityState(), new Condition() {
          @Override
          public boolean value(Object o) {
            return (! project.isOpen()) || project.isDisposed();
          }
        });
      } else {
        final ModalityState notNullModalityState = modalityState == null ? ModalityState.NON_MODAL : modalityState;
        application.invokeLater(command, notNullModalityState, project.getDisposed());
      }
    }
  }

  public static void execute(ProgressIndicator pi) {
    if (pi.isShowing()) {
      final long maxWait = 3000;
      final long start = System.currentTimeMillis();
      while ((! pi.isPopupWasShown()) && (pi.isRunning()) && (System.currentTimeMillis() - maxWait < start)) {
        final Object lock = new Object();
        synchronized (lock) {
          try {
            lock.wait(100);
          }
          catch (InterruptedException e) {
            //
          }
        }
      }
      ProgressManager.checkCanceled();
    }
  }
}