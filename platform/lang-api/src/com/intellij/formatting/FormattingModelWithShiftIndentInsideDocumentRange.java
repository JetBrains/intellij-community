/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.TextRange;

/**
 * Defines the psi-based formatting model that is responsible for multiline AST nodes indentation
 * at the stage of document-based formatting. The <code>shiftIndentInsideDocumentRange</code> method
 * is called in the model, instead of standard
 * {@link FormattingModel#shiftIndentInsideRange(ASTNode, TextRange, int)} procedure.
 */
public interface FormattingModelWithShiftIndentInsideDocumentRange extends FormattingModelEx {
  /**
   * Indents every line except for the first in the specified text range representing a multiline block
   * by the specified amount.
   *
   * @param document the document for modification.
   * @param node     the owner of the text range, if defined.
   * @param range    the text range to indent.
   * @param indent   the amount by which every line should be indented.
   * @return the text range covering the block with added indents or null for call of default procedure.
   */
  TextRange shiftIndentInsideDocumentRange(Document document, ASTNode node, TextRange range, int indent);

  /**
   * Adjusts indentation space before the multiline AST node.
   *
   * @param node        the multiline node.
   * @param whiteSpace  the leading space for the node after formatting procedure.
   * @return the adjusted indent (or <code>whiteSpace</code> if no correction needs).
   */
  String adjustWhiteSpaceInsideDocument(ASTNode node, String whiteSpace);
}
