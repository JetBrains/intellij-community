/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */
package com.intellij.formatting;

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
}
