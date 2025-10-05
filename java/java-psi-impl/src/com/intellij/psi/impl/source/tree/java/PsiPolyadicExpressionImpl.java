// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.scope.ElementClassHint;
import com.intellij.psi.scope.PatternResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiPolyadicExpressionImpl extends ExpressionPsiElement implements PsiPolyadicExpression {
  private static final Logger LOG = Logger.getInstance(PsiPolyadicExpressionImpl.class);

  public PsiPolyadicExpressionImpl() {
    super(JavaElementType.POLYADIC_EXPRESSION);
  }

  @Override
  public @NotNull IElementType getOperationTokenType() {
    return ((PsiJavaToken)findChildByRoleAsPsiElement(ChildRole.OPERATION_SIGN)).getTokenType();
  }

  @Override
  public PsiJavaToken getTokenBeforeOperand(@NotNull PsiExpression operand) {
    PsiElement element = operand;
    while(element != null) {
      if (getChildRole(element.getNode()) == ChildRole.OPERATION_SIGN) return (PsiJavaToken)element;
      element = element.getPrevSibling();
    }
    return null;
  }

  @Override
  public PsiType getType() {
    return JavaResolveCache.getInstance(getProject()).getType(this, MY_TYPE_EVALUATOR);
  }

  private static final Function<PsiPolyadicExpressionImpl,PsiType> MY_TYPE_EVALUATOR = expression -> doGetType(expression);

  private static @Nullable PsiType doGetType(PsiPolyadicExpressionImpl param) {
    PsiExpression[] operands = param.getOperands();
    PsiType lType = null;

    IElementType sign = param.getOperationTokenType();
    for (int i=1; i<operands.length;i++) {
      PsiType rType = operands[i].getType();
      // optimization: if we can calculate type based on right type only
      PsiType type = TypeConversionUtil.calcTypeForBinaryExpression(null, rType, sign, false);
      if (type != TypeConversionUtil.NULL_TYPE) return type;
      if (lType == null) lType = operands[0].getType();
      lType = TypeConversionUtil.calcTypeForBinaryExpression(lType, rType, sign, true);
    }
    return lType;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    if (role == ChildRole.OPERATION_SIGN) {
      return findChildByType(OUR_OPERATIONS_BIT_SET);
    }
    return null;
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (OUR_OPERATIONS_BIT_SET.contains(child.getElementType())) {
      return ChildRole.OPERATION_SIGN;
    }
    return ChildRoleBase.NONE;
  }

  private static final TokenSet OUR_OPERATIONS_BIT_SET =
    TokenSet.create(JavaTokenType.OROR, JavaTokenType.ANDAND, JavaTokenType.OR, JavaTokenType.XOR, JavaTokenType.AND, JavaTokenType.EQEQ,
                    JavaTokenType.NE, JavaTokenType.LT, JavaTokenType.GT, JavaTokenType.LE, JavaTokenType.GE, JavaTokenType.LTLT,
                    JavaTokenType.GTGT, JavaTokenType.GTGTGT, JavaTokenType.PLUS, JavaTokenType.MINUS, JavaTokenType.ASTERISK, JavaTokenType.DIV,
                    JavaTokenType.PERC);


  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPolyadicExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiExpression @NotNull [] getOperands() {
    PsiExpression[] operands = cachedOperands;
    if (operands == null) {
      cachedOperands = operands = getChildrenAsPsiElements(ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
    }
    return operands;
  }

  @Override
  public boolean processDeclarations(@NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    return processDeclarations(this, processor, state, lastParent, place);
  }

  static boolean processDeclarations(@NotNull PsiPolyadicExpression expression,
                                     @NotNull PsiScopeProcessor processor,
                                     @NotNull ResolveState state,
                                     PsiElement lastParent,
                                     @NotNull PsiElement place) {
    IElementType tokenType = expression.getOperationTokenType();
    boolean isAndToken = tokenType.equals(JavaTokenType.ANDAND);
    boolean isOrToken = tokenType.equals(JavaTokenType.OROR);
    if (!isAndToken && !isOrToken) return true;
    ElementClassHint elementClassHint = processor.getHint(ElementClassHint.KEY);
    if (elementClassHint != null && !elementClassHint.shouldProcess(ElementClassHint.DeclarationKind.VARIABLE)) return true;
    PatternResolveState wantedHint = PatternResolveState.fromBoolean(isAndToken);
    if (state.get(PatternResolveState.KEY) == wantedHint.invert()) return true;
    return PsiScopesUtil.walkChildrenScopes(expression, processor, wantedHint.putInto(state), lastParent, place);
  }

  private volatile PsiExpression[] cachedOperands;
  @Override
  public void clearCaches() {
    cachedOperands = null;
    super.clearCaches();
  }

  @Override
  public String toString() {
    return "PsiPolyadicExpression: " + getText();
  }
}
