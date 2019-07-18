// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

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

  /**
   * If inner highlighters are muted for a collapsed fold region, there will be no visual indication
   * that region contains certain highlighters inside. By default such indication is added.
   *
   * @see com.intellij.openapi.editor.ex.RangeHighlighterEx#isVisibleIfFolded()
   */
  default void setInnerHighlightersMuted(boolean value) {}

  /**
   * @see #setInnerHighlightersMuted(boolean)
   */
  default boolean areInnerHighlightersMuted() { return false; }

  /**
   * By default, gutter mark (for collapsing/expanding the region using mouse) is not shown for a folding region, if it's contained within
   * a single document line. This method allows to change this behaviour for given fold region.
   *
   * @see #isGutterMarkEnabledForSingleLine()
   * @see EditorSettings#setAllowSingleLogicalLineFolding(boolean)
   */
  default void setGutterMarkEnabledForSingleLine(boolean value) {}

  /**
   * @see #setGutterMarkEnabledForSingleLine(boolean)
   */
  default boolean isGutterMarkEnabledForSingleLine() { return false; }

  /**
   * Updates region's placeholder text. Should be called inside {@link FoldingModel#runBatchFoldingOperation(Runnable)}, like any other
   * operations with fold regions.
   */
  default void setPlaceholderText(@NotNull String text) {}
}
