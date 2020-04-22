// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides services for highlighting ranges of text in a document, painting markers on the
 * gutter and so on, and for retrieving information about highlighted ranges.
 *
 * @see com.intellij.openapi.editor.Editor#getMarkupModel()
 * @see com.intellij.openapi.editor.impl.DocumentMarkupModel#forDocument(Document, com.intellij.openapi.project.Project, boolean)
 */
public interface MarkupModel extends UserDataHolder {
  /**
   * Returns the document to which the markup model is attached.
   *
   * @return the document instance.
   */
  @NotNull
  Document getDocument();

  /**
   * Adds a highlighter covering the specified range of the document, which can modify
   * the attributes used for text rendering, add a gutter marker and so on. Range highlighters are
   * {@link com.intellij.openapi.editor.RangeMarker} instances and use the same rules for tracking
   * the range after document changes.
   *
   * @param startOffset    the start offset of the range to highlight.
   * @param endOffset      the end offset of the range to highlight.
   * @param layer          relative priority of the highlighter (highlighters with higher
   *                       layer number override highlighters with lower layer number;
   *                       layer number values for standard IDE highlighters are defined in
   *                       {@link HighlighterLayer})
   * @param forcedTextAttributes forced attributes to use for highlighting,
   *                       or {@code null} if the textAttributeKey should be used,
   *                       or it doesn't modify the text attributes.
   * @param textAttributesKey the key to use for highlighting with the current color scheme,
   *                       or {@code null} if it doesn't modify the text attributes.
   * @param targetArea     type of highlighting (specific range or all full lines covered by the range).
   * @return the highlighter instance.
   */
  @NotNull
  RangeHighlighter addRangeHighlighter(int startOffset,
                                       int endOffset,
                                       int layer,
                                       @Nullable TextAttributes forcedTextAttributes,
                                       @Nullable TextAttributesKey textAttributesKey,
                                       @NotNull HighlighterTargetArea targetArea);

  /**
   * @deprecated Use the overload with TextAttributeKey
   */
  @Deprecated
  @NotNull
  default RangeHighlighter addRangeHighlighter(int startOffset,
                                       int endOffset,
                                       int layer,
                                       @Nullable TextAttributes textAttributes,
                                       @NotNull HighlighterTargetArea targetArea) {
    return addRangeHighlighter(startOffset, endOffset, layer, textAttributes, null, targetArea);
  }

  /**
   * Adds a highlighter covering the specified line in the document.
   *
   * @param line           the line number of the line to highlight.
   * @param layer          relative priority of the highlighter (highlighters with higher
   *                       layer number override highlighters with lower layer number;
   *                       layer number values for standard IDE highlighters are defined in
   *                       {@link HighlighterLayer})
   * @param forcedTextAttributes forced attributes to use for highlighting,
   *                       or {@code null} if the textAttributeKey should be used,
   *                       or it doesn't modify the text attributes.
   * @param textAttributesKey the key to use for highlighting with the current color scheme,
   *                       or {@code null} if it doesn't modify the text attributes.
   * @return the highlighter instance.
   */
  @NotNull
  RangeHighlighter addLineHighlighter(int line,
                                      int layer,
                                      @Nullable TextAttributes forcedTextAttributes,
                                      @Nullable TextAttributesKey textAttributesKey);

  /**
   * @deprecated Use the overload with TextAttributeKey
   */
  @Deprecated
  @NotNull
  default RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes) {
    return addLineHighlighter(line, layer, textAttributes, null);
  }

  /**
   * Removes the specified highlighter instance.
   *
   * @param rangeHighlighter the highlighter to remove.
   */
  void removeHighlighter(@NotNull RangeHighlighter rangeHighlighter);

  /**
   * Removes all highlighter instances.
   */
  void removeAllHighlighters();

  /**
   * Returns all highlighter instances contained in the model.
   *
   * @return the array of highlighter instances.
   */
  RangeHighlighter @NotNull [] getAllHighlighters();
}
