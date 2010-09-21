/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.formatting;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

/**
 * Represents a model of the document containing the formatted text, as seen by the
 * formatter. Allows a formatter to access information about the document.
 *
 * @see com.intellij.formatting.FormattingModel#getDocumentModel()
 */

public interface FormattingDocumentModel {
  /**
   * Returns the line number corresponding to the specified offset in the document.
   * @param offset the offset for which the line number is requested.
   * @return the line number corresponding to the offset.
   */
  int getLineNumber(int offset);

  /**
   * Returns the offset corresponding to the start of the specified line in the document.
   * @param line the line number for which the offset is requested.
   * @return the start offset of the line.
   */
  int getLineStartOffset(int line);

  /**
   * Returns the text contained in the specified text range of the document.
   * @param textRange the text range for which the text is requested.
   * @return the text at the specified text range.
   */
  CharSequence getText(final TextRange textRange);

  /**
   * Returns the length of the entire document text.
   * @return the document text length.
   */
  int getTextLength();

  Document getDocument();

  /**
   * Allows to answer if all document symbols from <code>[startOffset; endOffset)</code> region are treated as white spaces by formatter.
   *
   * @param startOffset     target start document offset (inclusive)
   * @param endOffset       target end document offset (exclusive)
   * @return                <code>true</code> if all document symbols from <code>[startOffset; endOffset)</code> region are treated
   *                        as white spaces by formatter; <code>false</code> otherwise
   */
  boolean containsWhiteSpaceSymbolsOnly(int startOffset, int endOffset);

  /**
   * Allows to answer if given symbol is treated by the current model as white space symbol during formatting.
   *
   * @param symbol    symbols to check
   * @return          <code>true</code> if given symbol is treated by the current model as white space symbol during formatting;
   *                  <code>false</code> otherwise
   */
  boolean isWhiteSpaceSymbol(char symbol);
}
