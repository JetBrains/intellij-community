/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.CharTable;
import org.jetbrains.annotations.NotNull;

public class PsiExpressionListImpl extends CompositePsiElement implements PsiExpressionList {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiExpressionListImpl");

  public PsiExpressionListImpl() {
    super(JavaElementType.EXPRESSION_LIST);
  }

  @Override
  @NotNull
  public PsiExpression[] getExpressions() {
    return getChildrenAsPsiElements(ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
  }

  @Override
  public int getExpressionCount() {
    return countChildren(ElementType.EXPRESSION_BIT_SET);
  }

  @Override
  public boolean isEmpty() {
    return findChildByType(ElementType.EXPRESSION_BIT_SET) == null;
  }

  @Override
  @NotNull
  public PsiType[] getExpressionTypes() {
    PsiExpression[] expressions = getExpressions();
    PsiType[] types = PsiType.createArray(expressions.length);

    for (int i = 0; i < types.length; i++) {
      types[i] = expressions[i].getType();
    }

    return types;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      default:
        return null;

      case ChildRole.LPARENTH:
        return getFirstChildNode() != null && getFirstChildNode().getElementType() == JavaTokenType.LPARENTH ? getFirstChildNode() : null;

      case ChildRole.RPARENTH:
        if (getLastChildNode() != null && getLastChildNode().getElementType() == JavaTokenType.RPARENTH) {
          return getLastChildNode();
        }
        else {
          return null;
        }
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.COMMA) {
      return ChildRole.COMMA;
    }
    else if (i == JavaTokenType.LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == JavaTokenType.RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION_IN_LIST;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public TreeElement addInternal(TreeElement first, ASTNode last, ASTNode anchor, Boolean before) {
    final CharTable treeCharTab = SharedImplUtil.findCharTableByTree(this);
    if (anchor == null) {
      if (before == null || before.booleanValue()) {
        anchor = findChildByRole(ChildRole.RPARENTH);
        if (anchor == null) {
          LeafElement lparenth = Factory.createSingleLeafElement(JavaTokenType.LPARENTH, "(", 0, 1, treeCharTab, getManager());
          super.addInternal(lparenth, lparenth, null, Boolean.FALSE);
          LeafElement rparenth = Factory.createSingleLeafElement(JavaTokenType.RPARENTH, ")", 0, 1, treeCharTab, getManager());
          super.addInternal(rparenth, rparenth, null, Boolean.TRUE);
          anchor = findChildByRole(ChildRole.RPARENTH);
          LOG.assertTrue(anchor != null);
        }
        before = Boolean.TRUE;
      }
      else {
        anchor = findChildByRole(ChildRole.LPARENTH);
        if (anchor == null) {
          LeafElement lparenth = Factory.createSingleLeafElement(JavaTokenType.LPARENTH, "(", 0, 1, treeCharTab, getManager());
          super.addInternal(lparenth, lparenth, null, Boolean.FALSE);
          LeafElement rparenth = Factory.createSingleLeafElement(JavaTokenType.RPARENTH, ")", 0, 1, treeCharTab, getManager());
          super.addInternal(rparenth, rparenth, null, Boolean.TRUE);
          anchor = findChildByRole(ChildRole.LPARENTH);
          LOG.assertTrue(anchor != null);
        }
        before = Boolean.FALSE;
      }
    }
    TreeElement firstAdded = super.addInternal(first, last, anchor, before);
    if (ElementType.EXPRESSION_BIT_SET.contains(first.getElementType())) {
      JavaSourceUtil.addSeparatingComma(this, first, ElementType.EXPRESSION_BIT_SET);
    }
    return firstAdded;
  }

  @Override
  public void deleteChildInternal(@NotNull ASTNode child) {
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) {
      JavaSourceUtil.deleteSeparatingComma(this, child);
    }

    super.deleteChildInternal(child);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpressionList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiExpressionList";
  }
}
