// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class TypeParameterListElement extends CompositeElement {
  private static final Logger LOG = Logger.getInstance(TypeParameterListElement.class);
  private static final TokenSet TYPE_PARAMETER_SET = TokenSet.create(JavaElementType.TYPE_PARAMETER);

  public TypeParameterListElement() {
    super(JavaElementType.TYPE_PARAMETER_LIST);
  }

  @Override
  public int getChildRole(@NotNull final ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    final IElementType elType = child.getElementType();
    if (elType == JavaElementType.TYPE_PARAMETER) {
      return ChildRole.TYPE_PARAMETER_IN_LIST;
    }
    else if (elType == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (elType == JavaTokenType.LT) {
      return ChildRole.LT_IN_TYPE_LIST;
    }
    else if (elType == JavaTokenType.GT) {
      return ChildRole.GT_IN_TYPE_LIST;
    }
    else {
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public TreeElement addInternal(final TreeElement first, final ASTNode last, ASTNode anchor, Boolean before) {
    TreeElement lt = (TreeElement)findChildByRole(ChildRole.LT_IN_TYPE_LIST);
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (lt == null) {
      lt = Factory.createSingleLeafElement(JavaTokenType.LT, "<", 0, 1, treeCharTab, getManager());
      super.addInternal(lt, lt, getFirstChildNode(), Boolean.TRUE);
    }

    TreeElement gt = (TreeElement)findChildByRole(ChildRole.GT_IN_TYPE_LIST);
    if (gt == null) {
      gt = Factory.createSingleLeafElement(JavaTokenType.GT, ">", 0, 1, treeCharTab, getManager());
      super.addInternal(gt, gt, getLastChildNode(), Boolean.FALSE);
    }

    if (anchor == null) {
      if (before == null || before.booleanValue()){
        anchor = gt;
        before = Boolean.TRUE;
      }
      else{
        anchor = lt;
        before = Boolean.FALSE;
      }
    }

    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    if (first == last && first.getElementType() == JavaElementType.TYPE_PARAMETER) {
      JavaSourceUtil.addSeparatingComma(this, first, TYPE_PARAMETER_SET);
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull final ASTNode child) {
    if (child.getElementType() == JavaElementType.TYPE_PARAMETER){
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);

    if (child.getElementType() == JavaElementType.TYPE_PARAMETER) {
      final ASTNode lt = findChildByRole(ChildRole.LT_IN_TYPE_LIST);
      final ASTNode next = PsiImplUtil.skipWhitespaceAndComments(lt.getTreeNext());
      if (next != null && next.getElementType() == JavaTokenType.GT) {
        deleteChildInternal(lt);
        deleteChildInternal(next);
      }
    }
  }
}
