// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class UIExperiment {
  private static boolean NEW_DEBUGGER_UI_ENABLED = false;

  public static boolean isNewDebuggerUIEnabled() {
    return Registry.is("debugger.new.tool.window.layout", false) || NEW_DEBUGGER_UI_ENABLED;
  }

  public static void setNewDebuggerUIEnabled(boolean state) {
    NEW_DEBUGGER_UI_ENABLED = state;
  }
}
