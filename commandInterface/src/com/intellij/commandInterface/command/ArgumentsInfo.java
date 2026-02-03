// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.commandInterface.command;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.Nullable;

/**
 * Information about command {@link Argument arguments} and their value
 * validation.
 * Check optparse manual, package info and {@link Argument}
 * manual for more info about arguments.
 *
 * @author Ilya.Kazakevich
 */
public interface ArgumentsInfo {
  /**
   * Returns argument by its position. It also returns hint whether argument is required or not.
   *
   * @param argumentPosition argument position
   * @return null if no argument value is available at this position.
   * Returns argument otherwise. Boolean here should tell you if argument is required (command is invalid with out of it) or optional (
   * it is acceptible, but command can work with our of it)
   */
  @Nullable
  Pair<Boolean, Argument> getArgument(int argumentPosition);
}
