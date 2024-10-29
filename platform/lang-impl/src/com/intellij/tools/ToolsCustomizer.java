// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools;

import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.extensions.ExtensionPointName;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

public abstract class ToolsCustomizer {
  public static final ExtensionPointName<ToolsCustomizer> EP_NAME = ExtensionPointName.create("com.intellij.toolsCustomizer");

  public static GeneralCommandLine customizeCommandLine(@NotNull GeneralCommandLine commandLine, @NotNull DataContext dataContext) {
    return StreamEx.of(EP_NAME.getExtensions()).foldLeft(commandLine, (context, customizer) ->
      customizer.customizeCommandLine(dataContext, commandLine));
  }

  public abstract @NotNull GeneralCommandLine customizeCommandLine(@NotNull DataContext dataContext, @NotNull GeneralCommandLine commandLine);
}