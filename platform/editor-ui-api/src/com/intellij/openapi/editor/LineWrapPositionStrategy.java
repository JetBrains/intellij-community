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
package com.intellij.openapi.editor;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines contract for the strategy that calculates the best place to apply wrap to particular line (sequence of characters).
 * <p/>
 * One possible use case for such functionality is a situation when user opens document with lines that are wider that editor's
 * visible area. We can represent such long strings in multiple visual line then and need to know the best place to insert
 * such virtual wrap.
 */
public interface LineWrapPositionStrategy {

  /**
   * Allows calculating the most appropriate position to wrap target line.
   *
   * @param document                          target document which text is being processed
   * @param project                           target project
   * @param startOffset                       start offset to use with the given text holder (exclusive)
   * @param endOffset                         end offset to use with the given text holder (exclusive)
   * @param maxPreferredOffset                this method is expected to do its best to return offset that belongs to
   *                                          {@code (startOffset; maxPreferredOffset]} interval. However, it's allowed
   *                                          to return value from {@code (maxPreferredOffset; endOffset)} interval
   *                                          unless {@code 'allowToBeyondMaxPreferredOffset'} if {@code 'false'}
   * @param allowToBeyondMaxPreferredOffset   indicates if it's allowed to return value from
   *                                          {@code (maxPreferredOffset; endOffset]} interval in case of inability to
   *                                          find appropriate offset from {@code (startOffset; maxPreferredOffset]} interval
   * @param isSoftWrap                        identifies if current request is for isSoftWrap wrap position
   * @return                                  offset from {@code (startOffset; endOffset)} interval where
   *                                          target line should be wrapped OR {@code -1} if no wrapping should be performed
   */
  int calculateWrapPosition(
    @NotNull Document document, @Nullable Project project, int startOffset, int endOffset, int maxPreferredOffset,
    boolean allowToBeyondMaxPreferredOffset, boolean isSoftWrap
  );

  /**
   * Serves the same purposes as {@link #calculateWrapPosition(Document, Project, int, int, int, boolean, boolean)} but is given the
   * editor that shows the target text. Implement this method instead of the one above if the wrap position depends on the editor
   * context, e.g. on its {@link Editor#getHighlighter() highlighter} or settings.
   * <p/>
   * The document which text is being processed is derived from the given editor, see {@link #getWrapDocument(Editor, boolean)}.
   *
   * @param editor                            target editor which text is being processed
   * @param startOffset                       start offset to use with the given text holder (exclusive)
   * @param endOffset                         end offset to use with the given text holder (exclusive)
   * @param maxPreferredOffset                this method is expected to do its best to return offset that belongs to
   *                                          {@code (startOffset; maxPreferredOffset]} interval. However, it's allowed
   *                                          to return value from {@code (maxPreferredOffset; endOffset)} interval
   *                                          unless {@code 'allowToBeyondMaxPreferredOffset'} if {@code 'false'}
   * @param allowToBeyondMaxPreferredOffset   indicates if it's allowed to return value from
   *                                          {@code (maxPreferredOffset; endOffset]} interval in case of inability to
   *                                          find appropriate offset from {@code (startOffset; maxPreferredOffset]} interval
   * @param isSoftWrap                        identifies if current request is for isSoftWrap wrap position
   * @return                                  offset from {@code (startOffset; endOffset)} interval where
   *                                          target line should be wrapped OR {@code -1} if no wrapping should be performed
   */
  default int calculateWrapPosition(
    @NotNull Editor editor, int startOffset, int endOffset, int maxPreferredOffset,
    boolean allowToBeyondMaxPreferredOffset, boolean isSoftWrap
  ) {
    return calculateWrapPosition(getWrapDocument(editor, isSoftWrap), editor.getProject(), startOffset, endOffset, maxPreferredOffset,
                                 allowToBeyondMaxPreferredOffset, isSoftWrap);
  }

  /**
   * Allows choosing the document that holds the text to wrap for the given editor.
   * <p/>
   * Soft wrapping is a UI-side layout activity, so it observes the document that the editor currently displays. Hard wrapping modifies
   * the text instead, so it has to work with the document that is authoritative for PSI, undo and persistence.
   *
   * @param editor                            target editor which text is being processed
   * @param isSoftWrap                        identifies if current request is for isSoftWrap wrap position
   * @return                                  document to calculate wrap positions against for the given editor
   */
  default @NotNull Document getWrapDocument(@NotNull Editor editor, boolean isSoftWrap) {
    return isSoftWrap ? editor.getElfDocument() : editor.getDocument();
  }

  /**
   *The method ensures:
   * - No breaks within surrogate pairs to prevent visual errors.
   * - Natural line breaks at spaces and tabs for Western-style text.
   * - Support for wrapping in Eastern languages by allowing breaks on specific Unicode ranges.
   * @param text the character sequence to analyze
   * @param offset the offset of the character in the sequence to evaluate
   * @return {@code true} if the line can be wrapped at the specified offset,
   *         {@code false} otherwise
   */
  default boolean canWrapLineAtOffset(CharSequence text, int offset) {
    char c = text.charAt(offset);
    // Ensure no break occurs within surrogate pairs.
     if (Character.isLowSurrogate(c)) {
      if (offset - 1 >= 0 && Character.isHighSurrogate(text.charAt(offset - 1))) {
        return false;
      }
    }
    int codePoint = Character.codePointAt(text, offset);
    return codePoint == ' ' || codePoint == '\t' || (codePoint >= 0x2f00 && codePoint < 0x10000 /* eastern languages unicode ranges */);
  }
}
