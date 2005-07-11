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

package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * A node in the AST tree. The AST is an intermediate parsing tree created by {@link PsiBuilder},
 * out of which a PSI tree is then created.
 * @author max
 * @see PsiElement
 */

public interface ASTNode extends UserDataHolder {
  ASTNode[] EMPTY_ARRAY = new ASTNode[0];

  /**
   * Returns the type of this node.
   * @return the element type.
   */
  IElementType getElementType();

  /**
   * Returns the text of this node.
   * @return the node text.
   */
  String getText();

  /**
   * Checks if the specified character is present in the text of this node.
   * @param c the character to search for.
   * @return true if the character is found, false otherwise.
   */
  boolean textContains(char c);

  /**
   * Returns the starting offset of the node text in the document.
   * @return the start offset.
   */
  int getStartOffset();

  /**
   * Returns the length of the node text.
   * @return the text length.
   */
  int getTextLength();

  /**
   * Returns the text range (a combination of starting offset in the document and length) for this node.
   * @return the text range.
   */
  TextRange getTextRange();

  ASTNode getTreeParent();

  ASTNode getFirstChildNode();

  ASTNode getLastChildNode();

  ASTNode getTreeNext();

  ASTNode getTreePrev();

  ASTNode[] getChildren(TokenSet filter);

  void addChild(@NotNull ASTNode child);

  void addChild(@NotNull ASTNode child, ASTNode anchorBefore);

  void removeChild(@NotNull ASTNode child);

  void removeRange(@NotNull ASTNode first, ASTNode firstWhichStayInTree);

  void replaceChild(@NotNull ASTNode oldChild, @NotNull ASTNode newChild);

  void replaceAllChildrenToChildrenOf(ASTNode anotherParent);

  void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore);

  Object clone();

  ASTNode copyElement();

  ASTNode findLeafElementAt(int offset);

  <T> T getCopyableUserData(Key<T> key);

  <T> void putCopyableUserData(Key<T> key, T value);

  ASTNode findChildByType(IElementType type);

  PsiElement getPsi();
}
