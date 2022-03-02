// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.tools.util.text;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

public final class LineOffsetsUtil {
  @NotNull
  public static LineOffsets create(@NotNull Document document) {
    return new LineOffsetsDocumentWrapper(document);
  }

  /**
   * NB: Does not support CRLF separators, use {@link StringUtil#convertLineSeparators}.
   */
  @NotNull
  public static LineOffsets create(@NotNull CharSequence text) {
    return LineOffsetsImpl.create(text);
  }
}
