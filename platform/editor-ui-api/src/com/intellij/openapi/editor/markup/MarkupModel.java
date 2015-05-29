/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Document;
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
   *                       layer number values for standard IDEA highlighters are given in
   *                       {@link HighlighterLayer} class)
   * @param textAttributes the attributes to use for highlighting, or null if the highlighter
   *                       does not modify the text attributes.
   * @param targetArea     type of highlighting (specific range or all full lines covered by the range).
   * @return the highlighter instance.
   */
  @NotNull
  RangeHighlighter addRangeHighlighter(int startOffset,
                                       int endOffset,
                                       int layer,
                                       @Nullable TextAttributes textAttributes,
                                       @NotNull HighlighterTargetArea targetArea);

  /**
   * Adds a highlighter covering the specified line in the document.
   *
   * @param line           the line number of the line to highlight.
   * @param layer          relative priority of the highlighter (highlighters with higher
   *                       layer number override highlighters with lower layer number;
   *                       layer number values for standard IDEA highlighters are given in
   *                       {@link HighlighterLayer} class)
   * @param textAttributes the attributes to use for highlighting, or null if the highlighter
   *                       does not modify the text attributes.
   * @return the highlighter instance.
   */
  @NotNull
  RangeHighlighter addLineHighlighter(int line, int layer, @Nullable TextAttributes textAttributes);

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
  @NotNull
  RangeHighlighter[] getAllHighlighters();
}
