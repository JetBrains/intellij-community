// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;

/**
 * Defines a mapping between document line number and the numbers displayed in gutter.
 *
 * @see EditorGutter#setLineNumberConverter(LineNumberConverter, LineNumberConverter)
 */
public interface LineNumberConverter {
  /**
   * Defines the number to be displayed in gutter for the given document line.
   *
   * @param lineNumber one-based line number
   * @return number to be displayed in gutter, {@code null} means no number is displayed
   */
  Integer convert(@NotNull Editor editor, int lineNumber);

  /**
   * Number which should be used to calculate width of line number area in gutter. This should be the largest number which will be
   * displayed. {@code null} means no width will be allocated to the line number area.
   */
  Integer getMaxLineNumber(@NotNull Editor editor);

  LineNumberConverter DEFAULT = new LineNumberConverter() {
    @Override
    public Integer convert(@NotNull Editor editor, int lineNumber) {
      return lineNumber;
    }

    @Override
    public Integer getMaxLineNumber(@NotNull Editor editor) {
      return editor.getDocument().getLineCount();
    }
  };
}
