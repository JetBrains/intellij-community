// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Represents a range of text in the document which has specific markup (special text attributes,
 * line marker, gutter icon, error stripe marker or line separator).
 *
 * @see MarkupModel#addRangeHighlighter(TextAttributesKey, int, int, int, HighlighterTargetArea)
 * @see MarkupModel#addLineHighlighter(TextAttributesKey, int, int)
 * @see com.intellij.lang.annotation.Annotation
 */
public interface RangeHighlighter extends RangeMarker {
  RangeHighlighter[] EMPTY_ARRAY = new RangeHighlighter[0];

  /**
   * Returns the relative priority of the highlighter (higher priority highlighters can override
   * lower priority ones; layer number values for standard IDE highlighters are defined in
   * {@link HighlighterLayer}).
   *
   * @return the highlighter priority.
   */
  int getLayer();

  /**
   * Returns the value indicating whether the highlighter affects a range of text or a sequence of
   * of entire lines in the specified range.
   *
   * @return the highlighter target area.
   */
  @NotNull
  HighlighterTargetArea getTargetArea();

  /**
   * Returns the text attributes key used for highlighting.
   * Having a key is preferred over raw attributes which makes it impossible to update it on a {@link EditorColorsScheme} changes
   * @return the attributes key or {@code null} if the highlighter does not have a key.
   */
  @Nullable
  TextAttributesKey getTextAttributesKey();

  /**
   * Sets the text attributes key used for highlighting.
   * Having a key is preferred over raw attributes which makes it impossible to update it on a {@link EditorColorsScheme} changes
   * @param textAttributesKey a attributes key.
   */
  void setTextAttributesKey(@NotNull TextAttributesKey textAttributesKey);

  /**
   * @deprecated Use the overload with {@link EditorColorsScheme} and prefer using {@link #getTextAttributesKey()}
   */
  @Nullable
  @Deprecated
  default TextAttributes getTextAttributes() {
    return getTextAttributes(null);
  }

  /**
   * Returns text attributes used for highlighting.
   * @param scheme color scheme for which text attributes are requested
   * @return the attributes, or null if highlighter does not modify the text attributes.
   * @see RangeHighlighter#getTextAttributesKey()
   */
  @Nullable
  TextAttributes getTextAttributes(@Nullable("when null, the global scheme will be used") EditorColorsScheme scheme);

  /**
   * Returns the renderer used for drawing line markers in the area covered by the
   * highlighter, and optionally for processing mouse events over the markers.
   * Line markers are drawn over the folding area and are used, for example,
   * to highlight modified lines in files under source control.
   *
   * @return the renderer instance, or {@code null} if the highlighter does not add any line markers.
   * @see ActiveGutterRenderer
   */
  @Nullable
  LineMarkerRenderer getLineMarkerRenderer();

  /**
   * Sets the renderer used for drawing line markers in the area covered by the
   * highlighter, and optionally for processing mouse events over the markers.
   * Line markers are drawn over the folding area and are used, for example,
   * to highlight modified lines in files under source control.
   *
   * @param renderer the renderer instance, or {@code null} if the highlighter does not add any line markers.
   * @see ActiveGutterRenderer
   */
  void setLineMarkerRenderer(@Nullable LineMarkerRenderer renderer);


  @Nullable
  CustomHighlighterRenderer getCustomRenderer();

  void setCustomRenderer(CustomHighlighterRenderer renderer);

  /**
   * Returns the renderer used for drawing gutter icons in the area covered by the
   * highlighter. Gutter icons are drawn to the left of the folding area and can be used,
   * for example, to mark implemented or overridden methods.
   *
   * @return the renderer instance, or {@code null} if the highlighter does not add any gutter icons.
   */
  @Nullable
  GutterIconRenderer getGutterIconRenderer();

  /**
   * Sets the renderer used for drawing gutter icons in the area covered by the
   * highlighter. Gutter icons are drawn to the left of the folding area and can be used,
   * for example, to mark implemented or overridden methods.
   *
   * @param renderer the renderer instance, or {@code null} if the highlighter does not add any gutter icons.
   */
  void setGutterIconRenderer(@Nullable GutterIconRenderer renderer);

  /**
   * @deprecated Use the overload with {@link EditorColorsScheme}
   */
  @Deprecated(forRemoval = true)
  @Nullable
  default Color getErrorStripeMarkColor() {
    return getErrorStripeMarkColor(null);
  }

  /**
   * Returns the color of the marker drawn in the error stripe in the area covered by the highlighter.
   *
   * @return the error stripe marker color, or {@code null} if the highlighter does not add any
   * error stripe markers.
   */
  @Nullable
  Color getErrorStripeMarkColor(@Nullable("when null, the global scheme will be used") EditorColorsScheme scheme);

  /**
   * Sets the color of the marker drawn in the error stripe in the area covered by the highlighter.
   *
   * @param color the error stripe marker color, or {@code null} if the highlighter does not add any
   *              error stripe markers.
   */
  void setErrorStripeMarkColor(@Nullable Color color);

  /**
   * Returns the object whose {@code toString()} method is called to get the text of the tooltip
   * for the error stripe marker added by the highlighter.
   *
   * @return the error stripe tooltip objects, or {@code null} if the highlighter does not add any error
   * stripe markers or the marker has no tooltip.
   */
  @Nullable
  Object getErrorStripeTooltip();

  /**
   * Sets the object whose {@code toString()} method is called to get the text of the tooltip
   * for the error stripe marker added by the highlighter.
   *
   * @param tooltipObject the error stripe tooltip objects, or {@code null} if the highlighter does not
   *                      add any error stripe markers or the marker has no tooltip.
   */
  void setErrorStripeTooltip(@Nullable @Tooltip Object tooltipObject);

  /**
   * Returns the value indicating whether the error stripe marker has reduced width (like
   * the markers used to highlight changed lines).
   *
   * @return {@code true} if the marker has reduced width, {@code false} otherwise.
   */
  boolean isThinErrorStripeMark();

  /**
   * Sets the value indicating whether the error stripe marker has reduced width (like
   * the markers used to highlight changed lines).
   *
   * @param value {@code true} if the marker has reduced width, {@code false} otherwise.
   */
  void setThinErrorStripeMark(boolean value);

  /**
   * Returns the color of the separator drawn above or below the range covered by
   * the highlighter.
   *
   * @return the separator color, or {@code null} if the highlighter does not add a line separator.
   */
  @Nullable
  Color getLineSeparatorColor();

  /**
   * Sets the color of the separator drawn above or below the range covered by
   * the highlighter.
   *
   * @param color the separator color, or {@code null} if the highlighter does not add a line separator.
   */
  void setLineSeparatorColor(@Nullable Color color);

  void setLineSeparatorRenderer(LineSeparatorRenderer renderer);

  LineSeparatorRenderer getLineSeparatorRenderer();

  /**
   * Returns the placement of the separator drawn by the range highlighter
   * (above or below the range).
   *
   * @return the separator placement, or {@code null} if the highlighter does not add a line separator.
   */
  @Nullable
  SeparatorPlacement getLineSeparatorPlacement();

  /**
   * Sets the placement of the separator drawn by the range highlighter
   * (above or below the range).
   *
   * @param placement the separator placement, or {@code null} if the highlighter does not add a line separator.
   */
  void setLineSeparatorPlacement(@Nullable SeparatorPlacement placement);

  /**
   * Sets the filter which can disable the highlighter in specific editor instances.
   *
   * @param filter the filter controlling the highlighter availability, or {@link MarkupEditorFilter#EMPTY} if
   *               highlighter is available in all editors.
   */
  void setEditorFilter(@NotNull MarkupEditorFilter filter);

  /**
   * Gets the filter which can disable the highlighter in specific editor instances.
   *
   * @return the filter controlling the highlighter availability. Default availability is controlled by {@link MarkupEditorFilter#EMPTY}.
   */
  @NotNull
  MarkupEditorFilter getEditorFilter();
}
