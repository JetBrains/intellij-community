// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

public abstract class ToolsCustomizer {
  public static final ExtensionPointName<ToolsCustomizer> EP_NAME = ExtensionPointName.create("com.intellij.toolsCustomizer");

  public static GeneralCommandLine customizeCommandLine(@NotNull GeneralCommandLine commandLine, @NotNull DataContext dataContext) {
    GeneralCommandLine result = commandLine;
    for (ToolsCustomizer customizer : EP_NAME.getExtensions()) {
      result = customizer.customizeCommandLine(dataContext, commandLine);
    }
    return result;
  }

  public abstract @NotNull GeneralCommandLine customizeCommandLine(@NotNull DataContext dataContext, @NotNull GeneralCommandLine commandLine);
}