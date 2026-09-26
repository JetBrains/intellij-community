// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class WaitForProgressToShow {
  private WaitForProgressToShow() {
  }

  /**
   * @deprecated Use {@link com.intellij.openapi.application.Application#invokeAndWait(Runnable)} instead.
   */
  @Deprecated
  public static void runOrInvokeAndWaitAboveProgress(@NotNull Runnable command) {
    runOrInvokeAndWaitAboveProgress(command, ModalityState.defaultModalityState());
  }

  /**
   * @deprecated Use {@link com.intellij.openapi.application.Application#invokeAndWait(Runnable, ModalityState)} instead.
   */
  @Deprecated
  public static void runOrInvokeAndWaitAboveProgress(@NotNull Runnable command,
                                                     @Nullable ModalityState modalityState) {
    if (modalityState == null) modalityState = ModalityState.defaultModalityState();
    ApplicationManager.getApplication().invokeAndWait(command, modalityState);
  }

  /**
   * @deprecated Use {@link ModalityUiUtil#invokeLaterIfNeeded(ModalityState, Runnable)} instead.
   */
  @Deprecated
  public static void runOrInvokeLaterAboveProgress(@NotNull Runnable command,
                                                   @Nullable ModalityState modalityState,
                                                   @NotNull Project project) {
    if (modalityState == null) modalityState = ModalityState.defaultModalityState();
    ModalityUiUtil.invokeLaterIfNeeded(modalityState, project.getDisposed(), command);
  }

  /**
   * Waits until modal dialog with progress indicator is shown on screen.
   *
   * @deprecated Violates threading assertions in ProgressWindow.
   */
  @ApiStatus.Internal
  @Deprecated
  public static void execute(ProgressIndicator pi) {
    if (pi.isShowing()) {
      final long maxWait = 3000;
      final long start = System.currentTimeMillis();
      while ((!pi.isPopupWasShown()) && (pi.isRunning()) && (System.currentTimeMillis() - maxWait < start)) {
        final Object lock = new Object();
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
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
