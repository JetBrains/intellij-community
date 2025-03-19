// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.openapi.util.registry.EarlyAccessRegistryManager;
import com.intellij.ui.ExperimentalUI;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class UIExperiment {
  public static boolean isNewDebuggerUIEnabled() {
    return ExperimentalUI.isNewUI() || EarlyAccessRegistryManager.INSTANCE.getBoolean("debugger.new.tool.window.layout");
  }
}
