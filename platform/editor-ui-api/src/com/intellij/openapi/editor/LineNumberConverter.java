// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a mapping between document line number and the numbers displayed in gutter.
 *
 * @see EditorGutter#setLineNumberConverter(LineNumberConverter, LineNumberConverter)
 * @see Increasing
 */
public interface LineNumberConverter {
  /**
   * Defines the number to be displayed in gutter for the given document line.
   *
   * @param lineNumber one-based line number
   * @return number to be displayed in gutter, {@code null} means no number is displayed
   */
  @Nullable
  Integer convert(@NotNull Editor editor, int lineNumber);

  /**
   * Number which should be used to calculate width of line number area in gutter. This should be the largest number which will be
   * displayed. {@code null} means no width will be allocated to the line number area.
   */
  @Nullable
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

  /**
   * Specialization of {@link LineNumberConverter} whose {@link #convert(Editor, int)} method
   * always produces monotonically increasing numbers.
   */
  interface Increasing extends LineNumberConverter {
    @Nullable
    @Override
    default Integer getMaxLineNumber(@NotNull Editor editor) {
      for (int i = editor.getDocument().getLineCount(); i > 0; i--) {
        Integer number = convert(editor, i);
        if (number != null) return number;
      }
      return null;
    }
  }
}
