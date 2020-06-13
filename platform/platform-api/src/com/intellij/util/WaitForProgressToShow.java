// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaitForProgressToShow {
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
        application.invokeAndWait(command);
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
        application.invokeLater(command, pi.getModalityState(), o -> (! project.isOpen()) || project.isDisposed());
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