// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a region of text in the editor which can be folded.
 *
 * @see FoldingModel#addFoldRegion(int, int, String)
 * @see FoldingModel#getAllFoldRegions()
 */
public interface FoldRegion extends RangeMarker {
  FoldRegion[] EMPTY_ARRAY = new FoldRegion[0];

  /**
   * If {@code Boolean.TRUE} value is set for this key on a collapsed fold region (see {@link #putUserData(Key, Object)}), 
   * there will not be a visual indication that region contains certain highlighters inside. By default such indication is added.
   * 
   * @see com.intellij.openapi.editor.markup.RangeHighlighter#VISIBLE_IF_FOLDED
   */
  Key<Boolean> MUTE_INNER_HIGHLIGHTERS = Key.create("mute.inner.highlighters");

  /**
   * Checks if the fold region is currently expanded.
   *
   * @return true if the fold region is expanded, false otherwise.
   */
  boolean isExpanded();

  /**
   * Expands or collapses the fold region.
   *
   * @param expanded true if the region should be expanded, false otherwise.
   */
  void setExpanded(boolean expanded);

  /**
   * Returns the placeholder text displayed when the fold region is collapsed.
   *
   * @return the placeholder text.
   */
  @NotNull
  String getPlaceholderText();

  Editor getEditor();

  @Nullable
  FoldingGroup getGroup();

  boolean shouldNeverExpand();
}
