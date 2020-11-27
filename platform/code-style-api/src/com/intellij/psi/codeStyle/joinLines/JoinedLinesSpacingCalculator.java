// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle.joinLines;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interface for fast calculation of spacing (in columns) for joined lines as an alternative to formatter-based logic.
 * Used in editor actions like Smart Indenting Backspace or Join Lines handling.
 * Assumes that spacing is calculating using the editor document {@code editor.getDocument()} and the current offset without
 * any document commits and PSI tree rebuilding which can be a time consuming operation. If there is no {@code JoinedLinesSpacingCalculator}
 * in for the current editor and language context OR if a {@code JoinedLinesSpacingCalculator} can't calculate the spacing (returns {@code -1}),
 * the document is committed and a formatter-based spacing calculation is performed.
 */
public interface JoinedLinesSpacingCalculator {
  /**
   * Calculates the spacing (in columns) for joined lines at given offset after join lines or smart backspace actions.
   *
   * @param editor   The editor for which the spacing must be returned.
   * @param language Context language
   * @param offset   Offset in the editor after the indent in the second joining line.
   * @return {@code -1}, if JoinedLinesSpacingCalculator can't calculate the spacing.
   */
  default int getJoinedLinesSpacing(@NotNull Project project, @NotNull Editor editor, @Nullable Language language, int offset) {
    return -1;
  }
}
