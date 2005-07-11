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
import com.intellij.openapi.util.TextRange;

/**
 * Defines a single folding region in the code.
 *
 * @author max
 * @see FoldingBuilder
 */
public class FoldingDescriptor {
  public static final FoldingDescriptor[] EMPTY = new FoldingDescriptor[0];

  private ASTNode myElement;
  private TextRange myRange;

  /**
   * Creates a folding region related to the specified AST node and covering the specified
   * text range.
   * @param node  The node to which the folding region is related. The node is then passed to
   *              {@link FoldingBuilder#getPlaceholderText(com.intellij.lang.ASTNode)} and
   *              {@link FoldingBuilder#isCollapsedByDefault(com.intellij.lang.ASTNode)}.
   * @param range The folded text range.
   */
  public FoldingDescriptor(final ASTNode node, final TextRange range) {
    myElement = node;
    myRange = range;
  }

  /**
   * Returns the node to which the folding region is related.
   * @return the node to which the folding region is related.
   */
  public ASTNode getElement() {
    return myElement;
  }

  /**
   * Returns the folded text range.
   * @return the folded text range.
   */
  public TextRange getRange() {
    return myRange;
  }
}
