// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class PsiSwitchLabelStatementImpl extends PsiSwitchLabelStatementBaseImpl implements PsiSwitchLabelStatement {
  private static final Logger LOG = Logger.getInstance(PsiSwitchLabelStatementImpl.class);

  public PsiSwitchLabelStatementImpl() {
    super(JavaElementType.SWITCH_LABEL_STATEMENT);
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch (role) {
      case ChildRole.CASE_KEYWORD: return findChildByType(JavaTokenType.CASE_KEYWORD);
      case ChildRole.DEFAULT_KEYWORD: return findChildByType(JavaTokenType.DEFAULT_KEYWORD);
      case ChildRole.CASE_EXPRESSION: return findChildByType(ElementType.EXPRESSION_BIT_SET);
      case ChildRole.COLON: return findChildByType(JavaTokenType.COLON);
      default: return null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == JavaTokenType.CASE_KEYWORD) return ChildRole.CASE_KEYWORD;
    if (i == JavaTokenType.DEFAULT_KEYWORD) return ChildRole.DEFAULT_KEYWORD;
    if (i == JavaTokenType.COLON) return ChildRole.COLON;
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) return ChildRole.CASE_EXPRESSION;
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchLabelStatement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSwitchLabelStatement";
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    if (!super.processDeclarations(processor, state, lastParent, place)) return false;

    // Do not resolve references that come from the list of elements in this case rule
    if (lastParent instanceof PsiCaseLabelElementList) return true;

    if (isNotImmediateSwitchLabel()) return true;

    final PsiCaseLabelElementList patternsInCaseLabel = getCaseLabelElementList();
    if (patternsInCaseLabel == null) return true;

    return patternsInCaseLabel.processDeclarations(processor, state, null, place);
  }

  /**
   * When the resolving is happening inside a {@link PsiCodeBlock} it traverses through all the case labels,
   * which is not what is expected for pattern variables, because their scope is bound only to the nearest case handler.
   * The method checks if this {@link PsiSwitchLabelStatement} is the nearest one to the case handler.
   *
   * @return true if the this {@link PsiSwitchLabelStatement} is followed by another {@link PsiSwitchLabelStatement}, false otherwise
   */
  private boolean isNotImmediateSwitchLabel() {
    final PsiElement rightNeighbour = PsiTreeUtil.skipWhitespacesForward(this);
    return rightNeighbour instanceof PsiSwitchLabelStatement;
  }
}