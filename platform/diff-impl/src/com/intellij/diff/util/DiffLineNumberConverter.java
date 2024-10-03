// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.LineNumberConverter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

@ApiStatus.Internal
public final class DiffLineNumberConverter implements LineNumberConverter {
  private final @Nullable IntPredicate myFoldingHiddenLines;
  private final @Nullable IntUnaryOperator myLineConverter;

  public DiffLineNumberConverter(@Nullable IntPredicate foldingPredicate, @Nullable IntUnaryOperator function) {
    myFoldingHiddenLines = foldingPredicate;
    myLineConverter = function;
  }

  @Override
  public Integer convert(@NotNull Editor editor, int lineNumber) {
    return convertImpl(lineNumber, true);
  }

  @Override
  public Integer getMaxLineNumber(@NotNull Editor editor) {
    for (int i = editor.getDocument().getLineCount(); i > 0; i--) {
      Integer number = convertImpl(i, false);
      if (number != null) {
        return number;
      }
    }
    return 0;
  }

  public @Nullable Integer convertImpl(int lineNumber, boolean hideFoldingLines) {
    int zeroBaseNumber = lineNumber - 1;
    if (hideFoldingLines && myFoldingHiddenLines != null && myFoldingHiddenLines.test(zeroBaseNumber)) {
      return null;
    }
    if (myLineConverter != null) {
      zeroBaseNumber = myLineConverter.applyAsInt(zeroBaseNumber);
    }
    return zeroBaseNumber < 0 ? null : zeroBaseNumber + 1;
  }

  public @Nullable String convertLineNumberToStringImpl(int lineNumber, boolean hideFoldingLines) {
    Integer converted = convertImpl(lineNumber, hideFoldingLines);
    return converted == null ? null : String.valueOf(converted);
  }

  public boolean hasCustomLineNumbers() {
    return myLineConverter != null;
  }
}
