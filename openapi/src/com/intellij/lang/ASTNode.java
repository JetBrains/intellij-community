package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 18, 2005
 * Time: 6:56:08 PM
 * To change this template use File | Settings | File Templates.
 */
public interface ASTNode extends UserDataHolder {
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

  Object clone();
}
