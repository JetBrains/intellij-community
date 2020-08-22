// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source;

import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public final class IdentityCharTable implements CharTable {
  private IdentityCharTable() { }

  public static final IdentityCharTable INSTANCE = new IdentityCharTable();

  @NotNull
  @Override
  public CharSequence intern(@NotNull final CharSequence text) {
    return text;
  }

  @NotNull
  @Override
  public CharSequence intern(@NotNull CharSequence baseText, int startOffset, int endOffset) {
    if (startOffset == 0  && endOffset == baseText.length()) return baseText;
    return baseText.subSequence(startOffset, endOffset);
  }
}
