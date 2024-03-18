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
  @NotNull
  private final @NlsSafe String myName;
  @Nullable
  private final Help myHelp;
  @NotNull
  private final ArgumentsInfo myArgumentsInfo;
  @NotNull
  private final List<Option> myOptions = new ArrayList<>();
  @NotNull
  private final CommandExecutor myExecutor;

  /**
   *
   * @param name command name
   * @param help command help (if available)
   * @param argumentsInfo command arguments
   * @param executor engine to execute command
   * @param options command options
   * @param onCommandSuccessExecutedRunnable runnable to be run when command is executed with exit code 0
   */
  public SimpleCommand(@NotNull final @NlsSafe String name,
                       @Nullable final Help help,
                       @NotNull final ArgumentsInfo argumentsInfo,
                       @NotNull final CommandExecutor executor,
                       @NotNull final Collection<Option> options,
                       @Nullable final Runnable onCommandSuccessExecutedRunnable) {
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
  public SimpleCommand(@NotNull final @NlsSafe String name,
                       @Nullable final Help help,
                       @NotNull final ArgumentsInfo argumentsInfo,
                       @NotNull final CommandExecutor executor,
                       @NotNull final Collection<Option> options) {
    this(name, help, argumentsInfo, executor, options, null);
  }

  @NotNull
  @Override
  public @NlsSafe String getName() {
    return myName;
  }

  @Nullable
  @Override
  public Help getHelp(final boolean tryCutOutArguments) {
    if (!tryCutOutArguments || myHelp == null) {
      return myHelp;
    }
    // Cut out arguments like [] and <> from text
    final @NlsSafe String newHelpString = myHelp.getHelpString().replaceAll("(\\[[^\\]]+\\]|<[^>]+>|\\.{3,}|^" + myName + ')', "").trim();
    return new Help(newHelpString, myHelp.getExternalHelpUrl());
  }

  @NotNull
  @Override
  public ArgumentsInfo getArgumentsInfo() {
    return myArgumentsInfo;
  }

  @NotNull
  @Override
  public List<Option> getOptions() {
    return Collections.unmodifiableList(myOptions);
  }

  @Override
  public void execute(@NotNull final String commandName,
                      @NotNull final Module module,
                      @NotNull final List<String> parameters,
                      @Nullable final ConsoleView consoleView,
                      @Nullable final Runnable onExecuted) {
    myExecutor.execute(myName, module, parameters, consoleView, onExecuted);
  }

  @NotNull
  public CommandExecutor getExecutor() {
    return myExecutor;
  }
}
