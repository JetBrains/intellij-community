/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ChildRole;
import com.intellij.psi.impl.source.tree.ElementType;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.tree.ChildRoleBase;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;

public class PsiConditionalExpressionImpl extends ExpressionPsiElement implements PsiConditionalExpression {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.tree.java.PsiConditionalExpressionImpl");

  public PsiConditionalExpressionImpl() {
    super(JavaElementType.CONDITIONAL_EXPRESSION);
  }

  @Override
  @NotNull
  public PsiExpression getCondition() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.CONDITION);
  }

  @Override
  public PsiExpression getThenExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.THEN_EXPRESSION);
  }

  @Override
  public PsiExpression getElseExpression() {
    return (PsiExpression)findChildByRoleAsPsiElement(ChildRole.ELSE_EXPRESSION);
  }

  /**
   * JLS 15.25
   */
  @Override
  public PsiType getType() {
    PsiExpression expr1 = getThenExpression();
    PsiExpression expr2 = getElseExpression();
    PsiType type1 = expr1 == null ? null : expr1.getType();
    PsiType type2 = expr2 == null ? null : expr2.getType();
    if (type1 == null) return type2;
    if (type2 == null) return type1;

    if (type1.equals(type2)) return type1;

    if (PsiUtil.isLanguageLevel8OrHigher(this) &&
        PsiPolyExpressionUtil.isPolyExpression(this) &&
        !MethodCandidateInfo.ourOverloadGuard.currentStack().contains(PsiUtil.skipParenthesizedExprUp(this.getParent()))) {
      //15.25.3 Reference Conditional Expressions 
      // The type of a poly reference conditional expression is the same as its target type.
      final PsiType targetType = InferenceSession.getTargetType(this);
      if (targetType instanceof PsiClassType) {
        return ((PsiClassType)targetType).setLanguageLevel(PsiUtil.getLanguageLevel(this));
      }
      return targetType;
    }

    final int typeRank1 = TypeConversionUtil.getTypeRank(type1);
    final int typeRank2 = TypeConversionUtil.getTypeRank(type2);

    // bug in JLS3, see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6888770
    if (type1 instanceof PsiClassType && type2.equals(PsiPrimitiveType.getUnboxedType(type1))) return type2;
    if (type2 instanceof PsiClassType && type1.equals(PsiPrimitiveType.getUnboxedType(type2))) return type1;

    if (TypeConversionUtil.isNumericType(typeRank1) && TypeConversionUtil.isNumericType(typeRank2)){
      if (typeRank1 == TypeConversionUtil.BYTE_RANK && typeRank2 == TypeConversionUtil.SHORT_RANK) {
        return type2 instanceof PsiPrimitiveType ? type2 : PsiPrimitiveType.getUnboxedType(type2);
      }
      if (typeRank1 == TypeConversionUtil.SHORT_RANK && typeRank2 == TypeConversionUtil.BYTE_RANK) {
        return type1 instanceof PsiPrimitiveType ? type1 : PsiPrimitiveType.getUnboxedType(type1);
      }
      if (typeRank2 == TypeConversionUtil.INT_RANK && (typeRank1 == TypeConversionUtil.BYTE_RANK || typeRank1 == TypeConversionUtil.SHORT_RANK || typeRank1 == TypeConversionUtil.CHAR_RANK)){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type1, expr2)) return type1;
      }
      if (typeRank1 == TypeConversionUtil.INT_RANK && (typeRank2 == TypeConversionUtil.BYTE_RANK || typeRank2 == TypeConversionUtil.SHORT_RANK || typeRank2 == TypeConversionUtil.CHAR_RANK)){
        if (TypeConversionUtil.areTypesAssignmentCompatible(type2, expr1)) return type2;
      }
      return TypeConversionUtil.binaryNumericPromotion(type1, type2);
    }
    if (TypeConversionUtil.isNullType(type1) && !(type2 instanceof PsiPrimitiveType)) return type2;
    if (TypeConversionUtil.isNullType(type2) && !(type1 instanceof PsiPrimitiveType)) return type1;

    if (TypeConversionUtil.isAssignable(type1, type2, false)) return type1;
    if (TypeConversionUtil.isAssignable(type2, type1, false)) return type2;
    if (!PsiUtil.isLanguageLevel5OrHigher(this)) {
      return null;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type1)) {
      type1 = ((PsiPrimitiveType)type1).getBoxedType(this);
      if (type1 == null) return null;
    }
    if (TypeConversionUtil.isPrimitiveAndNotNull(type2)) {
      type2 = ((PsiPrimitiveType)type2).getBoxedType(this);
      if (type2 == null) return null;
    }

    if (type1 instanceof PsiLambdaParameterType || type2 instanceof PsiLambdaParameterType) return null;
    final PsiType leastUpperBound = GenericsUtil.getLeastUpperBound(type1, type2, getManager());
    return leastUpperBound != null ? PsiUtil.captureToplevelWildcards(leastUpperBound, this) : null;
  }

  @Override
  public ASTNode findChildByRole(int role) {
    LOG.assertTrue(ChildRole.isUnique(role));
    switch(role){
      default:
        return null;

      case ChildRole.CONDITION:
        return getFirstChildNode();

      case ChildRole.QUEST:
        return findChildByType(JavaTokenType.QUEST);

      case ChildRole.THEN_EXPRESSION:
        ASTNode quest = findChildByRole(ChildRole.QUEST);
        ASTNode child = quest.getTreeNext();
        while(true){
          if (child == null) return null;
          if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())) break;
          child = child.getTreeNext();
        }
        return child;

      case ChildRole.COLON:
        return findChildByType(JavaTokenType.COLON);

      case ChildRole.ELSE_EXPRESSION:
        ASTNode colon = findChildByRole(ChildRole.COLON);
        if (colon == null) return null;
        return ElementType.EXPRESSION_BIT_SET.contains(getLastChildNode().getElementType()) ? getLastChildNode() : null;
    }
  }

  @Override
  public int getChildRole(@NotNull ASTNode child) {
    LOG.assertTrue(child.getTreeParent() == this);
    if (ElementType.EXPRESSION_BIT_SET.contains(child.getElementType())){
      int role = getChildRole(child, ChildRole.CONDITION);
      if (role != ChildRoleBase.NONE) return role;
      role = getChildRole(child, ChildRole.THEN_EXPRESSION);
      if (role != ChildRoleBase.NONE) return role;
      role = getChildRole(child, ChildRole.ELSE_EXPRESSION);
      return role;
    }
    if (child.getElementType() == JavaTokenType.QUEST){
      return ChildRole.QUEST;
    }
    if (child.getElementType() == JavaTokenType.COLON){
      return ChildRole.COLON;
    }
    return ChildRoleBase.NONE;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitConditionalExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiConditionalExpression:" + getText();
  }
}

