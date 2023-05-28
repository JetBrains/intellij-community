// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class UIExperiment {
  private static boolean NEW_DEBUGGER_UI_ENABLED = false;

  public static boolean isNewDebuggerUIEnabled() {
    return NEW_DEBUGGER_UI_ENABLED || EarlyAccessRegistryManager.INSTANCE.getBoolean("debugger.new.tool.window.layout");
  }

  public static void setNewDebuggerUIEnabled(boolean state) {
    NEW_DEBUGGER_UI_ENABLED = state;
  }
}
