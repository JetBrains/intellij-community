// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaPsiUtil;
import com.intellij.codeInspection.dataFlow.NullabilityProblemKind;
import com.intellij.codeInspection.dataFlow.jvm.JvmPsiRangeSetUtil;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeType;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Objects;

class AnalysisStartingPoint {
  final DfType myDfType;
  final PsiExpression myAnchor;

  AnalysisStartingPoint(DfType type, PsiExpression anchor) {
    myDfType = type;
    myAnchor = anchor;
  }

  @Nullable AnalysisStartingPoint tryMeet(@NotNull AnalysisStartingPoint next) {
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(this.myAnchor, next.myAnchor)) return null;
    DfType meet = this.myDfType.meet(next.myDfType);
    if (meet == DfType.BOTTOM) return null;
    return new AnalysisStartingPoint(meet, this.myAnchor);
  }

  @Nullable AnalysisStartingPoint tryJoin(@NotNull AnalysisStartingPoint next) {
    if (!EquivalenceChecker.getCanonicalPsiEquivalence().expressionsAreEquivalent(this.myAnchor, next.myAnchor)) return null;
    DfType meet = this.myDfType.join(next.myDfType);
    if (meet == DfType.TOP) return null;
    return new AnalysisStartingPoint(meet, this.myAnchor);
  }

  static @Nullable AnalysisStartingPoint create(@NotNull DfType type, @Nullable PsiExpression anchor) {
    anchor = extractAnchor(anchor);
    if (anchor == null) return null;
    if (DfTypes.typedObject(anchor.getType(), Nullability.UNKNOWN).meet(type) == DfType.BOTTOM) return null;
    return new AnalysisStartingPoint(type, anchor);
  }

  @Nullable
  static PsiExpression extractAnchor(@Nullable PsiExpression target) {
    target = PsiUtil.skipParenthesizedExprDown(target);
    if (target instanceof PsiReferenceExpression ||
        target instanceof PsiMethodCallExpression ||
        target != null && propagateThroughExpression(target, DfTypes.LONG) != null) {
      return target;
    }
    return null;
  }

  static @Nullable AnalysisStartingPoint fromCondition(@Nullable PsiExpression cond) {
    cond = PsiUtil.skipParenthesizedExprDown(cond);
    if (cond == null) return null;
    if (cond instanceof PsiPolyadicExpression) {
      IElementType tokenType = ((PsiPolyadicExpression)cond).getOperationTokenType();
      if (tokenType.equals(JavaTokenType.ANDAND)) {
        AnalysisStartingPoint analysis = null;
        for (PsiExpression operand : ((PsiPolyadicExpression)cond).getOperands()) {
          AnalysisStartingPoint next = fromCondition(operand);
          if (next == null) return null;
          if (analysis == null) {
            analysis = next;
          }
          else {
            analysis = analysis.tryMeet(next);
            if (analysis == null) return null;
          }
        }
        return analysis;
      }
      if (tokenType.equals(JavaTokenType.OROR)) {
        AnalysisStartingPoint analysis = null;
        for (PsiExpression operand : ((PsiPolyadicExpression)cond).getOperands()) {
          AnalysisStartingPoint next = fromCondition(operand);
          if (next == null) return null;
          if (analysis == null) {
            analysis = next;
          }
          else {
            analysis = analysis.tryJoin(next);
            if (analysis == null) return null;
          }
        }
        return analysis;
      }
    }
    if (cond instanceof PsiBinaryExpression binop) {
      PsiExpression left = PsiUtil.skipParenthesizedExprDown(binop.getLOperand());
      PsiExpression right = PsiUtil.skipParenthesizedExprDown(binop.getROperand());
      AnalysisStartingPoint analysis = fromBinOp(left, binop.getOperationTokenType(), right);
      if (analysis != null) return analysis;
      return fromBinOp(right, binop.getOperationTokenType(), left);
    }
    if (cond instanceof PsiInstanceOfExpression) {
      PsiTypeElement checkType = ((PsiInstanceOfExpression)cond).getCheckType();
      if (checkType == null) return null;
      PsiExpression anchor = extractAnchor(((PsiInstanceOfExpression)cond).getOperand());
      if (anchor != null) {
        DfType typedObject = DfTypes.typedObject(checkType.getType(), Nullability.NOT_NULL);
        return new AnalysisStartingPoint(typedObject, anchor);
      }
    }
    if (cond instanceof PsiMethodCallExpression call && MethodCallUtils.isEqualsCall(call)) {
      PsiExpression qualifier = PsiUtil.skipParenthesizedExprDown(call.getMethodExpression().getQualifierExpression());
      PsiExpression argument = PsiUtil.skipParenthesizedExprDown(ArrayUtil.getFirstElement(call.getArgumentList().getExpressions()));
      if (qualifier != null && argument != null) {
        DfType type = fromConstant(qualifier);
        PsiExpression anchor = extractAnchor(argument);
        if (type == null) {
          type = fromConstant(argument);
          anchor = extractAnchor(qualifier);
        }
        if (type != null && anchor != null) {
          PsiType anchorType = anchor.getType();
          if (anchorType == null || DfTypes.typedObject(anchorType, Nullability.NOT_NULL).meet(type) == DfType.BOTTOM) return null;
          return new AnalysisStartingPoint(type, anchor);
        }
      }
    }
    if (BoolUtils.isNegation(cond)) {
      AnalysisStartingPoint negatedAnalysis = fromCondition(BoolUtils.getNegated(cond));
      return tryNegate(negatedAnalysis);
    }
    PsiExpression anchor = extractAnchor(cond);
    if (anchor != null) {
      return new AnalysisStartingPoint(DfTypes.TRUE, anchor);
    }
    return null;
  }

  static @Nullable AnalysisStartingPoint tryNegate(AnalysisStartingPoint analysis) {
    if (analysis == null) return null;
    DfType type = analysis.myDfType.tryNegate();
    if (type == null) return null;
    NullabilityProblemKind.NullabilityProblem<?> problem = NullabilityProblemKind.fromContext(analysis.myAnchor, Collections.emptyMap());
    if (problem != null && !(type instanceof DfPrimitiveType) &&
        CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION.equals(problem.thrownException())) {
      type = type.meet(DfTypes.NOT_NULL_OBJECT);
    }
    return new AnalysisStartingPoint(type, analysis.myAnchor);
  }

  static @Nullable DfType fromConstant(@NotNull PsiExpression constant) {
    if (constant instanceof PsiClassObjectAccessExpression classObject) {
      PsiTypeElement operand = classObject.getOperand();
      return DfTypes.referenceConstant(operand.getType(), classObject.getType());
    }
    if (constant instanceof PsiReferenceExpression refExpr) {
      PsiElement target = refExpr.resolve();
      if (target instanceof PsiEnumConstant) {
        return DfTypes.referenceConstant(target, Objects.requireNonNull(constant.getType()));
      }
    }
    if (ExpressionUtils.isNullLiteral(constant)) {
      return DfTypes.NULL;
    }
    Object value = ExpressionUtils.computeConstantExpression(constant);
    if (value != null) {
      return DfTypes.constant(value, Objects.requireNonNull(constant.getType()));
    }
    return null;
  }

  private static @Nullable AnalysisStartingPoint fromBinOp(@Nullable PsiExpression target,
                                                           @NotNull IElementType type,
                                                           @Nullable PsiExpression constant) {
    if (constant == null) return null;
    DfType constantType = fromConstant(constant);
    if (constantType == null) {
      return null;
    }
    PsiExpression anchor = extractAnchor(target);
    if (anchor != null) {
      PsiType anchorType = anchor.getType();
      if (anchorType == null || TypeUtils.isJavaLangString(anchorType)) return null;
      if (anchorType.equals(PsiTypes.byteType()) || anchorType.equals(PsiTypes.charType()) || anchorType.equals(PsiTypes.shortType())) {
        anchorType = PsiTypes.intType();
      }
      if (constantType == DfTypes.NULL || DfTypes.typedObject(anchorType, Nullability.NOT_NULL).meet(constantType) != DfType.BOTTOM) {
        if (type.equals(JavaTokenType.EQEQ)) {
          return new AnalysisStartingPoint(constantType, anchor);
        }
        if (type.equals(JavaTokenType.NE)) {
          return tryNegate(new AnalysisStartingPoint(constantType, anchor));
        }
      }
    }
    RelationType relationType = DfaPsiUtil.getRelationByToken(type);
    if (relationType != null) {
      LongRangeSet set = DfLongType.extractRange(constantType).fromRelation(relationType);
      if (anchor == null) return null;
      PsiType anchorType = anchor.getType();
      if (PsiTypes.longType().equals(anchorType)) {
        return new AnalysisStartingPoint(DfTypes.longRange(set), anchor);
      }
      if (PsiTypes.intType().equals(anchorType) ||
          PsiTypes.shortType().equals(anchorType) ||
          PsiTypes.byteType().equals(anchorType) ||
          PsiTypes.charType().equals(anchorType)) {
        set = set.meet(Objects.requireNonNull(JvmPsiRangeSetUtil.typeRange(anchorType)));
        return new AnalysisStartingPoint(DfTypes.intRangeClamped(set), anchor);
      }
    }
    return null;
  }

  static AnalysisStartingPoint propagateThroughExpression(@NotNull PsiElement expression, @NotNull DfType origType) {
    AnalysisStartingPoint analysis = null;
    if (origType == DfTypes.TRUE) {
      analysis = fromCondition((PsiExpression)expression);
    }
    else if (origType == DfTypes.FALSE) {
      analysis = tryNegate(fromCondition((PsiExpression)expression));
    }
    DfIntegralType dfType = ObjectUtils.tryCast(origType, DfIntegralType.class);
    if (dfType != null) {
      LongRangeType lrType = dfType.getLongRangeType();
      LongRangeSet origRange = dfType.getRange();
      LongRangeSet newRange = null;
      PsiExpression anchor = null;
      if (expression instanceof PsiPrefixExpression) {
        anchor = ((PsiPrefixExpression)expression).getOperand();
        IElementType type = ((PsiPrefixExpression)expression).getOperationTokenType();
        if (type.equals(JavaTokenType.MINUS)) {
          newRange = origRange.negate(lrType);
        }
        else if (type.equals(JavaTokenType.TILDE)) {
          newRange = origRange.negate(lrType).minus(LongRangeSet.point(1), lrType);
        }
      }
      if (expression instanceof PsiBinaryExpression binOp) {
        IElementType type = binOp.getOperationTokenType();
        LongRangeSet leftRange = CommonDataflow.getExpressionRange(binOp.getLOperand());
        LongRangeSet rightRange = CommonDataflow.getExpressionRange(binOp.getROperand());
        Long left = leftRange == null ? null : leftRange.getConstantValue();
        Long right = rightRange == null ? null : rightRange.getConstantValue();
        if (type.equals(JavaTokenType.PERC)) {
          if (right != null) {
            newRange = LongRangeSet.fromRemainder(right, origRange);
            anchor = binOp.getLOperand();
          }
        }
        if (type.equals(JavaTokenType.PLUS)) {
          if (right != null) {
            newRange = origRange.minus(LongRangeSet.point(right), lrType);
            anchor = binOp.getLOperand();
          }
          else if (left != null) {
            newRange = origRange.minus(LongRangeSet.point(left), lrType);
            anchor = binOp.getROperand();
          }
        }
        if (type.equals(JavaTokenType.MINUS)) {
          if (right != null) {
            newRange = origRange.plus(LongRangeSet.point(right), lrType);
            anchor = binOp.getLOperand();
          }
          else if (left != null) {
            newRange = LongRangeSet.point(left).minus(origRange, lrType);
            anchor = binOp.getROperand();
          }
        }
      }
      if (newRange != null && anchor != null && !(anchor instanceof PsiLiteralExpression)) {
        analysis = new AnalysisStartingPoint(DfTypes.rangeClamped(newRange, lrType), anchor);
      }
    }
    return analysis;
  }
}
