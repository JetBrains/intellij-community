// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Provides facilities for interning CharSequences
 */
public interface CharTable {
  Key<CharTable> CHAR_TABLE_KEY = new Key<>("Char table");

  /**
   * Interns the specified text and returns the interned character sequence.
   */
  @NotNull
  CharSequence intern(@NotNull CharSequence text);

  /**
   * Interns the substring of the specified base text defined by the start and end offsets,
   * and returns the interned character sequence.
   */
  @NotNull
  CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset);
}
