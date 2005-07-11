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
package com.intellij.lang.folding;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Document;

/**
 * Allows a custom language plugin to define rules for folding code in the language handled
 * by the plugin.
 *
 * @author max
 * @see com.intellij.lang.Language#getFoldingBuilder()
 */

public interface FoldingBuilder {
  /**
   * Builds the folding regions for the specified node in the AST tree and its children.
   *
   * @param node     the node for which folding is requested.
   * @param document the document for which folding is built. Can be used to retrieve line
   *                 numbers for folding regions.
   * @return the array of folding descriptors.
   */
  FoldingDescriptor[] buildFoldRegions(ASTNode node, Document document);

  /**
   * Returns the text which is displayed in the editor for the folding region related to the
   * specified node when the folding region is collapsed.
   *
   * @param node the node for which the placeholder text is requested.
   * @return the placeholder text.
   */
  String getPlaceholderText(ASTNode node);

  /**
   * Returns the default collapsed state for the folding region related to the specified node.
   *
   * @param node the node for which the collapsed state is requested.
   * @return true if the region is collapsed by default, false otherwise.
   */
  boolean isCollapsedByDefault(ASTNode node);
}
