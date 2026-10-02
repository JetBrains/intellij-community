// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.openapi.ui.ComponentContainer;
import org.jetbrains.annotations.NonNls;

/**
 * Represents component displaying the result of executing the process.
 * It can be a console, a test results view, or another similar component.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
 */
public interface ExecutionConsole extends ComponentContainer {
  @NonNls String CONSOLE_CONTENT_ID = "ConsoleContent";
}
