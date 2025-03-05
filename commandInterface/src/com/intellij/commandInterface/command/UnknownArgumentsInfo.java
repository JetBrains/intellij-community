// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * For many commands we know nothing about arguments but their help text.
 * This strategy is for this case
 *
 * @author Ilya.Kazakevich
 */
public final class UnknownArgumentsInfo implements ArgumentsInfo {
  /**
   * Argument help text
   */
  private final @NotNull Help myHelp;

  /**
   * @param allArgumentsHelpText argument help text
   */
  public UnknownArgumentsInfo(final @NotNull Help allArgumentsHelpText) {
    myHelp = allArgumentsHelpText;
  }


  @Override
  public @Nullable Pair<Boolean, Argument> getArgument(final int argumentPosition) {
    return Pair.create(false, new Argument(myHelp));
  }
}
