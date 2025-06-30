// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiParenthesizedExpressionImpl extends ExpressionPsiElement implements PsiParenthesizedExpression, Constants {
  private static final Logger LOG = Logger.getInstance(PsiParenthesizedExpressionImpl.class);

  public PsiParenthesizedExpressionImpl() {
    super(PARENTH_EXPRESSION);
  }

  @Override
  public @Nullable PsiExpression getExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.EXPRESSION);
  }

  @Override
  public PsiType getType() {
    PsiExpression expr = getExpression();
    if (expr == null) return null;
    return expr.getType();
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      case ChildRole.LPARENTH:
        return findChildByType(LPARENTH);

      case ChildRole.RPARENTH:
        return findChildByType(RPARENTH);

      case ChildRole.EXPRESSION:
        return findChildByType(EXPRESSION_BIT_SET);

      default:
        return null;

    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    IElementType i = child.getElementType();
    if (i == LPARENTH) {
      return ChildRole.LPARENTH;
    }
    else if (i == RPARENTH) {
      return ChildRole.RPARENTH;
    }
    else {
      if (EXPRESSION_BIT_SET.contains(child.getElementType())) {
        return ChildRole.EXPRESSION;
      }
      return ChildRoleBase.NONE;
    }
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitParenthesizedExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;
    return processDeclarations(processor, state, lastParent, place, this::getExpression);
  }

  public static boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                            @NotNull ResolveState state,
                                            PsiElement lastParent,
                                            @NotNull PsiElement place,
                                            @NotNull Computable<? extends @Nullable PsiElement> expressionProducer) {
    if (lastParent != null) return true;
    PsiElement expression = expressionProducer.get();
    if (expression == null) return true;
    return expression.processDeclarations(processor, state, null, place);
  }

  @Override
  public String toString() {
    return "PsiParenthesizedExpression:" + getText();
  }
}

