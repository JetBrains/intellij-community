// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Condition;
import org.jetbrains.annotations.NotNull;

public class AppUiUtil {
  public static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState);
    }
  }

  public static void invokeLaterIfNeeded(@NotNull Runnable runnable, @NotNull ModalityState modalityState, @NotNull Condition expired) {
    Application app = ApplicationManager.getApplication();
    if (app.isDispatchThread()) {
      runnable.run();
    }
    else {
      app.invokeLater(runnable, modalityState, expired);
    }
  }
}
