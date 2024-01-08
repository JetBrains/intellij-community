// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * TODO: Redundant?!
 * Case when command has no arguments (for sure!)
 *
 * @author Ilya.Kazakevich
 */
public final class NoArgumentsInfo implements ArgumentsInfo {
  /**
   * Instance to use when command has no arguments
   */
  public static final ArgumentsInfo INSTANCE = new NoArgumentsInfo();

  private NoArgumentsInfo() {
  }

  @Nullable
  @Override
  public Pair<Boolean, Argument> getArgument(final int argumentPosition) {
    return null;
  }
}
