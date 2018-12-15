// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.impl.source.tree.ElementType;
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
  public PsiExpression getExpression() {
    return (PsiExpression)findPsiChildByType(ElementType.EXPRESSION_BIT_SET);
  }

  @Override
  public PsiType getType() {
    if (PsiPolyExpressionUtil.isPolyExpression(this) &&
        !MethodCandidateInfo.ourOverloadGuard.currentStack().contains(PsiUtil.skipParenthesizedExprUp(getParent()))) {
      return InferenceSession.getTargetType(this);
    }

    List<PsiExpression> resultExpressions = PsiUtil.getSwitchResultExpressions(this);

    Set<PsiType> resultTypes = new HashSet<>();
    for (PsiExpression expression : resultExpressions) {
      PsiType resultExpressionType = expression.getType();
      if (resultExpressionType == null) return null;
      resultTypes.add(resultExpressionType);
    }

    //If the result expressions all have the same type (which may be the null type), then that is the type of the switch expression.
    if (resultTypes.size() == 1) {
      return ContainerUtil.getFirstItem(resultTypes);
    }

    //Otherwise, if the type of each result expression is boolean or Boolean, 
    //an unboxing conversion (5.1.8) is applied to each result expression of type Boolean, and the switch expression has type boolean.
    if (resultTypes.stream().allMatch(type -> PsiType.BOOLEAN.isAssignableFrom(type))) {
      return PsiType.BOOLEAN;
    }

    //Otherwise, if the type of each result expression is convertible to a numeric type (5.1.8), 
    // the type of the switch expression is given by numeric promotion (5.6.3) applied to the result expressions.
    int[] ranks = resultTypes.stream().mapToInt(type -> TypeConversionUtil.getTypeRank(type)).toArray();
    int maxRank = ArrayUtil.max(ranks);
    if (TypeConversionUtil.isNumericType(maxRank)) {
      if (maxRank == TypeConversionUtil.DOUBLE_RANK) {
        return PsiType.DOUBLE;
      }
      if (maxRank == TypeConversionUtil.FLOAT_RANK) {
        return PsiType.FLOAT;
      }
      if (maxRank == TypeConversionUtil.LONG_RANK) {
        return PsiType.LONG;
      }

      if (isNumericPromotion(resultExpressions, ranks, PsiType.CHAR)) {
        return PsiType.CHAR;
      }

      if (isNumericPromotion(resultExpressions, ranks, PsiType.SHORT)) {
        return PsiType.SHORT;
      }

      if (isNumericPromotion(resultExpressions, ranks, PsiType.BYTE)) {
        return PsiType.BYTE;
      }
      return PsiType.INT;
    }

    //Otherwise, boxing conversion (5.1.7) is applied to each result expression that has a primitive type, after which the type of the switch expression is the result of applying capture conversion (5.1.10) 
    // to the least upper bound (4.10.4) of the types of the result expressions.
    PsiType leastUpperBound = PsiType.NULL;
    for (PsiType type : resultTypes) {
      if (TypeConversionUtil.isPrimitiveAndNotNull(type)) {
        type = ((PsiPrimitiveType)type).getBoxedType(this);
      }
      if (leastUpperBound == PsiType.NULL) {
        leastUpperBound = type;
      }
      else {
        leastUpperBound = GenericsUtil.getLeastUpperBound(type, leastUpperBound, getManager());
      }
    }
    return leastUpperBound != null ? PsiUtil.captureToplevelWildcards(leastUpperBound, this) : null;
  }

  private static boolean isNumericPromotion(List<PsiExpression> resultExpressions, int[] ranks, final PsiPrimitiveType type) {
    return ArrayUtil.find(ranks, TypeConversionUtil.getTypeRank(type)) > -1 && 
           resultExpressions.stream().allMatch(expression -> TypeConversionUtil.areTypesAssignmentCompatible(type, expression));
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSwitchExpression(this);
    }
    else {
      super.accept(visitor);
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