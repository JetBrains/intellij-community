// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

public interface CharTable {
  Key<CharTable> CHAR_TABLE_KEY = new Key<>("Char table");

  @NotNull
  CharSequence intern(@NotNull CharSequence text);

  @NotNull
  CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset);
}
