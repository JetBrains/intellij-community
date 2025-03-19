// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Simple implementation of {@link Command}. It just structure that stores all info + {@link CommandExecutor} to execute commands.
 * It also delegates its execution to external {@link CommandExecutor}
 *
 * @author Ilya.Kazakevich
 */
public final class SimpleCommand implements Command {
  private final @NotNull @NlsSafe String myName;
  private final @Nullable Help myHelp;
  private final @NotNull ArgumentsInfo myArgumentsInfo;
  private final @NotNull List<Option> myOptions = new ArrayList<>();
  private final @NotNull CommandExecutor myExecutor;

  /**
   *
   * @param name command name
   * @param help command help (if available)
   * @param argumentsInfo command arguments
   * @param executor engine to execute command
   * @param options command options
   * @param onCommandSuccessExecutedRunnable runnable to be run when command is executed with exit code 0
   */
  public SimpleCommand(final @NotNull @NlsSafe String name,
                       final @Nullable Help help,
                       final @NotNull ArgumentsInfo argumentsInfo,
                       final @NotNull CommandExecutor executor,
                       final @NotNull Collection<Option> options,
                       final @Nullable Runnable onCommandSuccessExecutedRunnable) {
    myName = name;
    myHelp = help;
    myArgumentsInfo = argumentsInfo;
    myExecutor = executor;
    myOptions.addAll(options);
  }

  /**
   *
   * @param name command name
   * @param help command help (if available)
   * @param argumentsInfo command arguments
   * @param executor engine to execute command
   * @param options command options
   */
  public SimpleCommand(final @NotNull @NlsSafe String name,
                       final @Nullable Help help,
                       final @NotNull ArgumentsInfo argumentsInfo,
                       final @NotNull CommandExecutor executor,
                       final @NotNull Collection<Option> options) {
    this(name, help, argumentsInfo, executor, options, null);
  }

  @Override
  public @NotNull @NlsSafe String getName() {
    return myName;
  }

  @Override
  public @Nullable Help getHelp(final boolean tryCutOutArguments) {
    if (!tryCutOutArguments || myHelp == null) {
      return myHelp;
    }
    // Cut out arguments like [] and <> from text
    final @NlsSafe String newHelpString = myHelp.getHelpString().replaceAll("(\\[[^\\]]+\\]|<[^>]+>|\\.{3,}|^" + myName + ')', "").trim();
    return new Help(newHelpString, myHelp.getExternalHelpUrl());
  }

  @Override
  public @NotNull ArgumentsInfo getArgumentsInfo() {
    return myArgumentsInfo;
  }

  @Override
  public @NotNull List<Option> getOptions() {
    return Collections.unmodifiableList(myOptions);
  }

  @Override
  public void execute(final @NotNull String commandName,
                      final @NotNull Module module,
                      final @NotNull List<String> parameters,
                      final @Nullable ConsoleView consoleView,
                      final @Nullable Runnable onExecuted) {
    myExecutor.execute(myName, module, parameters, consoleView, onExecuted);
  }

  public @NotNull CommandExecutor getExecutor() {
    return myExecutor;
  }
}
