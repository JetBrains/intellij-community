// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Command with arguments and options
 *
 * @author Ilya.Kazakevich
 */
public interface Command extends CommandExecutor {


  /**
   * @return command name
   */
  @NotNull
  @NlsSafe String getName();

  /**
   * @param tryCutOutArguments Try to remove information about arguments from help text (i.e. "[file] removes file" -> "removes file").
   *                           Command may or may not support it.
   *                           It should ignore argument if it does not know how to cut out argument info.
   * @return Command help
   */
  @Nullable
  Help getHelp(boolean tryCutOutArguments);


  /**
   * @return Information about command positional, unnamed {@link Argument arguments} (not options!)
   */
  @NotNull
  ArgumentsInfo getArgumentsInfo();

  /**
   * @return command options
   */
  @NotNull
  List<Option> getOptions();

}
