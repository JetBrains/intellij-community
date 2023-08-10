// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ParameterListElement extends CompositeElement implements Constants {
  private static final Logger LOG = Logger.getInstance(ParameterListElement.class);

  private static final TokenSet PARAMETER_SET = TokenSet.create(JavaElementType.PARAMETER, JavaElementType.RECEIVER_PARAMETER);

  public ParameterListElement() {
    super(PARAMETER_LIST);
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    ensureParenthesisAroundParameterList();

    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByRole(ChildRole.RPARENTH);
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByRole(ChildRole.LPARENTH);
        before = Boolean.FALSE;
      }
    }

    TreeElement firstAdded = super.addInternal(first, last, anchor, before);

    if (first == last && PARAMETER_SET.contains(first.getElementType())) {
      JavaSourceUtil.addSeparatingComma(this, first, PARAMETER_SET);
    }
    
    return firstAdded;
  }

  private void ensureParenthesisAroundParameterList() {
    //lambda parameter without parenthesis
    if (findChildByRole(ChildRole.LPARENTH) == null) {
      addLeaf(JavaTokenType.LPARENTH, "(", getFirstChildNode());
      addLeaf(JavaTokenType.RPARENTH, ")", null);
    }
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    final TreeElement oldLastNodeInsideParens = getLastNodeInsideParens();
    final TreeElement oldFirstNodeInsideParens = getFirstNodeInsideParens();

    if (PARAMETER_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
      ensureParenthesisAroundParameterList();
    }

    super.deleteChildInternal(child);

    // We may want to fix trailing white space processing here - there is a following possible case:
    //    *) this parameter list is like (arg1, <white-space-containing-line-breaks>, arg2);
    //    *) 'arg2' is to be removed;
    // We don't want to keep trailing white space then
    TreeElement newLastNodeInsideParens = getLastNodeInsideParens();
    if (newLastNodeInsideParens != null && oldLastNodeInsideParens != null && newLastNodeInsideParens.getElementType() == WHITE_SPACE) {
      if (oldLastNodeInsideParens.getElementType() != WHITE_SPACE) {
        deleteChildInternal(newLastNodeInsideParens);
      }
      else {
        replaceChild(newLastNodeInsideParens, (ASTNode)oldLastNodeInsideParens.clone());
      }
    }

    final TreeElement newFirstNodeInsideParens = getFirstNodeInsideParens();
    if (newFirstNodeInsideParens != null && newFirstNodeInsideParens.getElementType() == WHITE_SPACE) {
      if (oldFirstNodeInsideParens == null || oldFirstNodeInsideParens.getElementType() != WHITE_SPACE) {
        deleteChildInternal(newFirstNodeInsideParens);
      }
      else {
        replaceChild(newFirstNodeInsideParens, (ASTNode)oldFirstNodeInsideParens.clone());
      }
    }
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LPARENTH:
        TreeElement firstNode = getFirstChildNode();
        return firstNode.getElementType() == LPARENTH ? firstNode : null;

      case ChildRole.RPARENTH:
        TreeElement lastNode = getLastChildNode();
        return lastNode.getElementType() == RPARENTH ? lastNode : null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (PARAMETER_SET.contains((i))) {
      return ChildRole.PARAMETER;
    }
    else if (i == COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == LPARENTH) {
      return getChildRole(child, ChildRole.LPARENTH);
    }
    else if (i == RPARENTH) {
      return getChildRole(child, ChildRole.RPARENTH);
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  /**
   * @return last node before closing right parenthesis if possible; {@code null} otherwise
   */
  @Nullable
  private TreeElement getLastNodeInsideParens() {
    TreeElement lastNode = getLastChildNode();
    return lastNode.getElementType() == RPARENTH ? lastNode.getTreePrev() : null;
  }

  /**
   * @return first node after opening left parenthesis if possible; {@code null} otherwise
   */
  @Nullable
  private TreeElement getFirstNodeInsideParens() {
    TreeElement firstNode = getFirstChildNode();
    return firstNode.getElementType() == LPARENTH ? firstNode.getTreeNext() : null;
  }
}
