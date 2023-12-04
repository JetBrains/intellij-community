// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A command that highlights the corresponding ranges in the editor if applicable. May do nothing if editor is not opened.
 * 
 * @param file virtual file to apply highlighting at
 * @param highlights highlights to apply
 */
public record ModHighlight(@NotNull VirtualFile file, @NotNull List<@NotNull HighlightInfo> highlights) implements ModCommand {
  @Override
  public boolean isEmpty() {
    return highlights().isEmpty();
  }

  /**
   * Single highlighting record
   * 
   * @param range range to highlight
   * @param attributesKey attributes to use
   * @param hideByTextChange whether highlighting should be removed on text change
   */
  public record HighlightInfo(@NotNull TextRange range, @NotNull TextAttributesKey attributesKey, boolean hideByTextChange) {
    /**
     * @param range new range
     * @return the same info but with updated range
     */
    public @NotNull HighlightInfo withRange(@NotNull TextRange range) {
      if (range.getStartOffset() == range().getStartOffset() && range.getEndOffset() == range().getEndOffset()) {
        return this;
      }
      return new HighlightInfo(range, attributesKey, hideByTextChange);
    }
  }
}
