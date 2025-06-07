// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.text;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public final class LineOffsetsUtil {
  public static @NotNull LineOffsets create(@NotNull Document document) {
    return new LineOffsetsDocumentWrapper(document);
  }

  /**
   * NB: Does not support CRLF separators, use {@link com.intellij.openapi.util.text.StringUtil#convertLineSeparators}.
   */
  public static @NotNull LineOffsets create(@NotNull CharSequence text) {
    return LineOffsetsImpl.create(text);
  }
}
