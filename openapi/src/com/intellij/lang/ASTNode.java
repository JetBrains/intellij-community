package com.intellij.lang;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 18, 2005
 * Time: 6:56:08 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ASTNode extends UserDataHolder {
  ASTNode[] EMPTY_ARRAY = new ASTNode[0];

  IElementType getElementType();

  String getText();

  boolean textContains(char c);

  int getStartOffset();

  int getTextLength();

  TextRange getTextRange();

  ASTNode getTreeParent();

  ASTNode getFirstChildNode();

  ASTNode getLastChildNode();

  ASTNode getTreeNext();

  ASTNode getTreePrev();

  ASTNode[] getChildren(TokenSet filter);

  void addChild(ASTNode child);

  void addChild(ASTNode child, ASTNode anchorBefore);

  void removeChild(ASTNode child);

  void replaceChild(ASTNode oldChild, ASTNode newChild);

  void replaceAllChildrenToChildrenOf(ASTNode anotherParent);

  void addChildren(ASTNode firstChild, ASTNode lastChild, ASTNode anchorBefore);

  Object clone();

  ASTNode copyElement();

  ASTNode findLeafElementAt(int offset);

  <T> T getCopyableUserData(Key<T> key);

  <T> void putCopyableUserData(Key<T> key, T value);

  ASTNode findChildByType(IElementType type);

  ASTNode[] findChildrenByFilter(TokenSet filter);

  PsiElement getPsi();
}
