// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.options;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a localized message to be displayed in options UI. Can be either simple message or split-string (see {@link #splitLabel()}).
 */
public sealed interface LocMessage permits PlainMessage {
  /**
   * @return a localized message
   */
  @NotNull @Nls String label();

  /**
   * @return a split string that contains a prefix and suffix, so the control could be placed in-between.
   * Split character is '|'. For example, if the string in resources is "At least | lines", then
   * the prefix is "At least " and the suffix is " lines", and the input field will be placed in-between
   */
  @NotNull default PrefixSuffix splitLabel() {
    String string = label();
    int splitPos = string.indexOf("|");
    return splitPos == -1 ? new PrefixSuffix(string, "") : new PrefixSuffix(string.substring(0, splitPos), string.substring(splitPos + 1));
  }
  
  record PrefixSuffix(@NotNull @Nls String prefix, @NotNull @Nls String suffix) {}
}
