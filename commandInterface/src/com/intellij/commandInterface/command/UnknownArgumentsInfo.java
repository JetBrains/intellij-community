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
  @NotNull
  private final Help myHelp;

  /**
   * @param allArgumentsHelpText argument help text
   */
  public UnknownArgumentsInfo(@NotNull final Help allArgumentsHelpText) {
    myHelp = allArgumentsHelpText;
  }


  @Nullable
  @Override
  public Pair<Boolean, Argument> getArgument(final int argumentPosition) {
    return Pair.create(false, new Argument(myHelp));
  }
}
