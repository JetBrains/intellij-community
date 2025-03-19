// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Executes command
 *
 * @author Ilya.Kazakevich
 */
public interface CommandExecutor {
  /**
   * @param commandName command name
   * @param module      module where execution takes place
   * @param parameters  command arguments
   * @param consoleView console view. If command is executed in console, this parameter is not null.
   *                    Command may use this console to output its result.
   *                    If command executes external process, it should call {@link ConsoleView#attachToProcess(ProcessHandler)}.
   * @param onExecuted  called when the execution successfully finished.
   */
  void execute(@NotNull String commandName,
               @NotNull Module module,
               @NotNull List<String> parameters,
               @Nullable ConsoleView consoleView,
               @Nullable Runnable onExecuted);
}
