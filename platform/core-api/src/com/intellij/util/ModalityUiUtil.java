// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public final class ModalityUiUtil {
  /**
   * Use this method when access any PSI, VirtualFiles, project/module model or other project settings, otherwise using
   * a corresponding method from UiUtil is allowed.<p/>

   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread under Write Intent lock, when IDE is in the specified modality
   * state (or a state with less modal dialogs open).
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *
   * @param modalityState    the state in which the runnable will be executed.
   * @param runnable the runnable to execute.
   */
  public static void invokeLaterIfNeeded(@NotNull ModalityState modalityState,
                                         @NotNull Runnable runnable) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState);
    }
  }

  /**
   * Use this method when access any PSI, VirtualFiles, project/module model or other project settings, otherwise using
   * a corresponding method from UiUtil is allowed.<p/>

   * Causes {@code runnable.run()} to be executed asynchronously on the
   * AWT event dispatching thread under Write Intent lock, when IDE is in the specified modality
   * state(or a state with less modal dialogs open) - unless the expiration condition is fulfilled.
   * This will happen after all pending AWT events have been processed.
   * <p>
   * Please use this method instead of {@link javax.swing.SwingUtilities#invokeLater(Runnable)} or {@link com.intellij.util.ui.UIUtil} methods
   * for the reasons described in {@link ModalityState} documentation.
   *  @param modalityState    the state in which the runnable will be executed.
   * @param expired  condition to check before execution.
   * @param runnable the runnable to execute.
   */
  public static void invokeLaterIfNeeded(@NotNull ModalityState modalityState,
                                         @NotNull Condition<?> expired, @NotNull Runnable runnable) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState, expired);
    }
  }

  /**
   * @deprecated Use ModalityUiUtil.invokeLaterIfNeeded(ModalityState, Runnable) instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable,
                                         @NotNull ModalityState modalityState) {
    invokeLaterIfNeeded(modalityState,runnable);
  }

  /**
   * @deprecated Use ModalityUiUtil.invokeLaterIfNeeded(ModalityState, Condition<?>, Runnable) instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState,
                                         @NotNull Condition<?> expired) {
    invokeLaterIfNeeded(modalityState, expired, runnable);
  }
}
