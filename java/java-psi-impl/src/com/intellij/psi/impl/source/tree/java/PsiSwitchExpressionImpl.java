// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSourceUtil;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PsiSwitchExpressionImpl extends PsiSwitchBlockImpl implements PsiSwitchExpression {
  public PsiSwitchExpressionImpl() {
    super(JavaElementType.SWITCH_EXPRESSION);
  }

  @Override
  public PsiType getType() {
    if (PsiUtil.isLanguageLevel8OrHigher(this) &&
        PsiPolyExpressionUtil.isPolyExpression(this) &&
        !MethodCandidateInfo.isOverloadCheck(PsiUtil.skipParenthesizedExprUp(getParent()))) {
      return InferenceSession.getTargetType(this);
    }

    List<PsiExpression> resultExpressions = PsiUtil.getSwitchResultExpressions(this);

    Set<PsiType> resultTypes = new HashSet<>();
    for (PsiExpression expression : resultExpressions) {
      PsiType resultExpressionType = expression.getType();
      if (resultExpressionType == null) return null;
      resultTypes.add(resultExpressionType);
    }

    if (resultTypes.isEmpty()) return null;

    //If the result expressions all have the same type (which may be the null type), then that is the type of the switch expression.
    if (resultTypes.size() == 1) {
      return ContainerUtil.getFirstItem(resultTypes);
    }

    //Otherwise, if the type of each result expression is boolean or Boolean, 
    //an unboxing conversion (5.1.8) is applied to each result expression of type Boolean, and the switch expression has type boolean.
    if (ContainerUtil.and(resultTypes, type -> PsiTypes.booleanType().isAssignableFrom(type))) {
      return PsiTypes.booleanType();
    }

    //Otherwise, if the type of each result expression is convertible to a numeric type (5.1.8), 
    // the type of the switch expression is given by numeric promotion (5.6.3) applied to the result expressions.
    int[] ranks = resultTypes.stream().mapToInt(type -> TypeConversionUtil.getTypeRank(type)).toArray();
    int maxRank = ArrayUtil.max(ranks);
    if (TypeConversionUtil.isNumericType(maxRank)) {
      if (maxRank == TypeConversionUtil.DOUBLE_RANK) {
        return PsiTypes.doubleType();
      }
      if (maxRank == TypeConversionUtil.FLOAT_RANK) {
        return PsiTypes.floatType();
      }
      if (maxRank == TypeConversionUtil.LONG_RANK) {
        return PsiTypes.longType();
      }

      if (isNumericPromotion(resultExpressions, ranks, PsiTypes.charType())) {
        return PsiTypes.charType();
      }

      if (isNumericPromotion(resultExpressions, ranks, PsiTypes.shortType())) {
        return PsiTypes.shortType();
      }

      if (isNumericPromotion(resultExpressions, ranks, PsiTypes.byteType())) {
        return PsiTypes.byteType();
      }
      return PsiTypes.intType();
    }

    //Otherwise, boxing conversion (5.1.7) is applied to each result expression that has a primitive type, after which the type of the switch expression is the result of applying capture conversion (5.1.10) 
    // to the least upper bound (4.10.4) of the types of the result expressions.
    PsiType leastUpperBound = PsiTypes.nullType();
    for (PsiType type : resultTypes) {
      if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
        type = ((PsiPrimitiveType)type).getBoxedType(this);
      }
      
      leastUpperBound = GenericsUtil.getLeastUpperBound(type, leastUpperBound, getManager());
    }
    return leastUpperBound != null ? PsiUtil.captureToplevelWildcards(leastUpperBound, this) : null;
  }

  private static boolean isNumericPromotion(List<PsiExpression> resultExpressions, int[] ranks, PsiPrimitiveType type) {
    return ArrayUtil.find(ranks, TypeConversionUtil.getTypeRank(type)) > -1 && 
           ContainerUtil.and(resultExpressions, expression -> TypeConversionUtil.areTypesAssignmentCompatible(type, expression));
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public void replaceChildInternal(@NotNull ASTNode child, @NotNull TreeElement newElement) {
    super.replaceChildInternal(child, JavaSourceUtil.addParenthToReplacedChild(child, newElement, getManager()));
  }

  @Override
  public String toString() {
    PsiExpression expression = getExpression();
    return "PsiSwitchExpression: " + (expression != null ? expression.getText() : "(incomplete)");
  }
}