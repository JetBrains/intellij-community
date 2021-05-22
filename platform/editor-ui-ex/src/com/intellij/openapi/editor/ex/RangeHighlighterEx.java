// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.ex;

import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.Comparator;

public interface RangeHighlighterEx extends RangeHighlighter, RangeMarkerEx {
  RangeHighlighterEx[] EMPTY_ARRAY = new RangeHighlighterEx[0];
  boolean isAfterEndOfLine();
  void setAfterEndOfLine(boolean value);

  int getAffectedAreaStartOffset();

  int getAffectedAreaEndOffset();

  @ApiStatus.Internal
  default @Nullable TextAttributes getForcedTextAttributes() {
    return null;
  }

  @ApiStatus.Internal
  default @Nullable Color getForcedErrorStripeMarkColor() {
    return null;
  }

  /**
   * Sets text attributes used for highlighting.
   * Manually set attributes have priority over {@link #getTextAttributesKey()}
   * during the calculation of {@link #getTextAttributes(EditorColorsScheme)}
   *
   * Can be also used to temporary hide the highlighter
   * {@link com.intellij.openapi.editor.markup.TextAttributes#ERASE_MARKER }
   */
  void setTextAttributes(@Nullable TextAttributes textAttributes);

  /**
   * @see #isVisibleIfFolded()
   */
  void setVisibleIfFolded(boolean value);

  /**
   * If {@code true}, there will be a visual indication that this highlighter is present inside a collapsed fold region.
   * By default it won't happen, use {@link #setVisibleIfFolded(boolean)} to change it.
   *
   * @see FoldRegion#setInnerHighlightersMuted(boolean)
   */
  boolean isVisibleIfFolded();

  /**
   * If {@code true}, this highlighter is persistent and is retained between code analyzer runs and IDE restarts.
   *
   * @see MarkupModelEx#addPersistentLineHighlighter(TextAttributesKey, int, int)
   * @see MarkupModelEx#addRangeHighlighterAndChangeAttributes(TextAttributesKey, int, int, int, HighlighterTargetArea, boolean, Consumer)
   */
  default boolean isPersistent() {
    return false;
  }

  default boolean isRenderedInGutter() {
    return getGutterIconRenderer() != null || getLineMarkerRenderer() != null;
  }

  /**
   * @deprecated Use {@link #getErrorStripeMarkColor(EditorColorsScheme)} directly,
   * it's impossible to tell if a highlighter should be rendered in a scroll bar since an editor can have a custom color scheme
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2021.2")
  default boolean isRenderedInScrollBar() {
    return getErrorStripeMarkColor(null) != null;
  }

  default void copyFrom(@NotNull RangeHighlighterEx other) {
    setAfterEndOfLine(other.isAfterEndOfLine());
    setGreedyToLeft(other.isGreedyToLeft());
    setGreedyToRight(other.isGreedyToRight());
    setVisibleIfFolded(other.isVisibleIfFolded());

    if (other.getForcedTextAttributes() != null) {
      setTextAttributes(other.getForcedTextAttributes());
    }
    if (other.getTextAttributesKey() != null) {
      setTextAttributesKey(other.getTextAttributesKey());
    }

    setLineMarkerRenderer(other.getLineMarkerRenderer());
    setCustomRenderer(other.getCustomRenderer());
    setGutterIconRenderer(other.getGutterIconRenderer());

    setErrorStripeMarkColor(other.getForcedErrorStripeMarkColor());
    setErrorStripeTooltip(other.getErrorStripeTooltip());
    setThinErrorStripeMark(other.isThinErrorStripeMark());

    setLineSeparatorColor(other.getLineSeparatorColor());
    setLineSeparatorPlacement(other.getLineSeparatorPlacement());
    setLineSeparatorRenderer(other.getLineSeparatorRenderer());

    setEditorFilter(other.getEditorFilter());
  }

  Comparator<RangeHighlighterEx> BY_AFFECTED_START_OFFSET = Comparator.comparingInt(RangeHighlighterEx::getAffectedAreaStartOffset);
}
