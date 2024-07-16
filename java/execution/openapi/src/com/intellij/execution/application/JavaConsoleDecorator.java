// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.application;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunConfigurationBase;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public abstract class JavaConsoleDecorator {
  private static final ExtensionPointName<JavaConsoleDecorator> EP_NAME = ExtensionPointName.create("com.intellij.java.consoleDecorator");

  public static @NotNull ConsoleView decorate(@NotNull ConsoleView console,
                                              @NotNull RunConfigurationBase<?> runConfiguration,
                                              @NotNull Executor executor) {
    ConsoleView result = console;
    for (JavaConsoleDecorator decorator : EP_NAME.getExtensionList()) {
      result = decorator.decorateConsole(result, runConfiguration, executor);
    }
    return result;
  }

  protected abstract @NotNull ConsoleView decorateConsole(@NotNull ConsoleView console,
                                                          @NotNull RunConfigurationBase<?> runConfiguration,
                                                          @NotNull Executor executor);
}
