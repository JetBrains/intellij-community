// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.openapi.util.TextRange;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public interface FormattingRangesInfo {
  boolean isWhitespaceReadOnly(@NotNull TextRange range);

  boolean isReadOnly(@NotNull TextRange range);

  boolean isOnInsertedLine(int offset);

  @NotNull
  @Unmodifiable
  List<TextRange> getTextRanges();

  /**
   * @return A range containing all ranges or null if no ranges.
   */
  @Nullable
  TextRange getBoundRange();
}
