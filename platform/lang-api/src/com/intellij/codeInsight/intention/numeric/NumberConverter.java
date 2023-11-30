// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.numeric;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An algorithm to convert a number to different representation while preserving its value
 * (e.g. decimal to hex).
 * @see AbstractNumberConversionIntention
 */
public interface NumberConverter {
  /**
   * Converts the supplied number to another representation
   * @param text original textual representation of the number (unary minus could be omitted for negative numbers) 
   * @param number numeric value of the number
   * @return the converted number or null if given number cannot be converted
   */
  @Nullable
  @Contract(pure = true)
  String getConvertedText(@NotNull String text, @NotNull Number number);

  /**
   * @return textual representation of this converter in lowercase (e.g. "hex" for converter which converts a number to hex).
   */
  @Override
  @Nls
  String toString();
}
