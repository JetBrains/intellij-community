/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.psi.impl.source.resolve.JavaResolveCache;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiPolyadicExpressionImpl extends ExpressionPsiElement implements PsiPolyadicExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiPolyadicExpressionImpl");

  public PsiPolyadicExpressionImpl() {
    super(JavaElementType.POLYADIC_EXPRESSION);
  }

  @Override
  @NotNull
  public IElementType getOperationTokenType() {
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

  private static final Function<PsiPolyadicExpressionImpl,PsiType> MY_TYPE_EVALUATOR =
    (NullableFunction<PsiPolyadicExpressionImpl, PsiType>)expression -> doGetType(expression);

  @Nullable
  private static PsiType doGetType(PsiPolyadicExpressionImpl param) {
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
    switch (role) {
      default:
        return null;

      case ChildRole.OPERATION_SIGN:
        return findChildByType(OUR_OPERATIONS_BIT_SET);
    }
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

  @NotNull
  @Override
  public PsiExpression[] getOperands() {
    PsiExpression[] operands = cachedOperands;
    if (operands == null) {
      cachedOperands = operands = getChildrenAsPsiElements(ElementType.EXPRESSION_BIT_SET, PsiExpression.ARRAY_FACTORY);
    }
    return operands;
  }

  private volatile PsiExpression[] cachedOperands;
  @Override
  public void clearCaches() {
    cachedOperands = null;
    super.clearCaches();
  }

  public String toString() {
    return "PsiPolyadicExpression: " + getText();
  }
}
