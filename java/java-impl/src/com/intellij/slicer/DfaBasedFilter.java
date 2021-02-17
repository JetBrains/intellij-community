// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.DfaNullability;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.DumbService;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.JavaPsiMathUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

final class DfaBasedFilter {
  private final @Nullable DfaBasedFilter myNextFilter;

  private final @NotNull DfType myDfType;

  private DfaBasedFilter(@Nullable DfaBasedFilter nextFilter, @NotNull DfType type) {
    myNextFilter = nextFilter;
    myDfType = type;
  }

  DfaBasedFilter(@NotNull DfType type) {
    this(null, type);
  }

  @NotNull DfType getDfType() {
    return myDfType;
  }
  
  DfaBasedFilter wrap() {
    return new DfaBasedFilter(this, DfTypes.TOP);
  }
  
  DfaBasedFilter unwrap() {
    return myNextFilter;
  }

  boolean allowed(@NotNull PsiElement element) {
    return allowed(element, true);
  }

  boolean requiresAssertionViolation(@NotNull PsiElement element) {
    return allowed(element, true) && !allowed(element, false);
  }

  private boolean allowed(@NotNull PsiElement element, boolean assertionsDisabled) {
    if (myDfType instanceof DfConstantType && element instanceof PsiLiteralValue) {
      DfConstantType<?> dfConstantType = (DfConstantType<?>)myDfType;
      Object constValue = dfConstantType.getValue();
      if (!(constValue instanceof PsiElement)) {
        Object literalValue = ((PsiLiteralValue)element).getValue();
        Object value = constValue == null ? literalValue : TypeConversionUtil.computeCastTo(literalValue, dfConstantType.getPsiType());
        return Objects.equals(value, constValue);
      }
    }
    DfType dfType = getElementDfType(element, assertionsDisabled);
    return dfType.meet(myDfType) != DfTypes.BOTTOM;
  }

  @Nullable DfaBasedFilter mergeFilter(@NotNull PsiElement element) {
    DfType type = getElementDfType(element, true);
    if (type instanceof DfReferenceType) {
      type = ((DfReferenceType)type).dropLocality().dropMutability();
    }
    DfType meet = type.meet(myDfType);
    if (meet == DfTypes.TOP && myNextFilter == null) return null;
    if (meet == DfTypes.BOTTOM || meet.equals(myDfType)) return this;
    return new DfaBasedFilter(myNextFilter, meet);
  }

  private @NotNull DfType getElementDfType(@NotNull PsiElement element, boolean assertionsDisabled) {
    if (!(element instanceof PsiExpression)) return DfTypes.TOP;
    PsiExpression expression = (PsiExpression)element;
    PsiType expressionType = expression.getType();
    if (TypeConversionUtil.isPrimitiveAndNotNull(expressionType) && myDfType instanceof DfReferenceType) {
      return DfTypes.typedObject(((PsiPrimitiveType)expressionType).getBoxedType(expression), Nullability.NOT_NULL);
    }
    if (!(expressionType instanceof PsiPrimitiveType) && myDfType instanceof DfPrimitiveType) {
      return DfTypes.typedObject(PsiPrimitiveType.getUnboxedType(expressionType), Nullability.NOT_NULL);
    }
    CommonDataflow.DataflowResult result = CommonDataflow.getDataflowResult(expression);
    if (result == null) return DfTypes.TOP;
    expression = PsiUtil.skipParenthesizedExprDown(expression);
    DfType type = assertionsDisabled ? result.getDfTypeNoAssertions(expression) : result.getDfType(expression);
    if (myDfType instanceof DfLongType && type instanceof DfIntType) {
      // Implicit widening conversion
      return DfTypes.longRange(((DfIntType)type).getRange());
    }
    if (type instanceof DfReferenceType) {
      SpecialField field = ((DfReferenceType)type).getSpecialField();
      if (field != null && !field.isStable()) {
        type = ((DfReferenceType)type).dropSpecialField();
      }
    }
    return type;
  }

  @Override
  public @NotNull String toString() {
    return myDfType.toString();
  }

  public @NotNull @Nls String getPresentationText(@NotNull PsiElement element) {
    if (element instanceof PsiLiteralExpression ||
        element instanceof PsiExpression && JavaPsiMathUtil.getNumberFromLiteral((PsiExpression)element) != null) {
      return "";
    }
    if (element instanceof PsiNewExpression && ((PsiNewExpression)element).isArrayCreation()) {
      return "";
    }
    return getPresentationText(myDfType, getElementType(element));
  }

  private @Nullable static PsiType getElementType(@NotNull PsiElement element) {
    if (DumbService.isDumb(element.getProject())) return null;
    if (element instanceof PsiExpression) {
      return ((PsiExpression)element).getType();
    }
    if (element instanceof PsiVariable) {
      return ((PsiVariable)element).getType();
    }
    return null;
  }

  static @Nls String getPresentationText(@NotNull DfType type, @Nullable PsiType psiType) {
    if (type == DfTypes.TOP) {
      return "";
    }
    if (type instanceof DfIntegralType) {
      LongRangeSet psiRange = LongRangeSet.fromType(psiType);
      LongRangeSet dfRange = ((DfIntegralType)type).getRange();
      if (psiRange != null && dfRange.contains(psiRange)) return "";
      // chop 'int' or 'long' prefix
      return dfRange.getPresentationText(psiType);
    }
    if (type instanceof DfConstantType) {
      return type.toString();
    }
    if (type instanceof DfReferenceType) {
      DfReferenceType stripped = ((DfReferenceType)type).dropNullability();
      DfaNullability nullability = ((DfReferenceType)type).getNullability();
      TypeConstraint constraint = ((DfReferenceType)type).getConstraint();
      if (constraint.getPresentationText(psiType).isEmpty()) {
        stripped = stripped.dropTypeConstraint();
      }
      @Nls String constraintText = stripped.toString();
      if (nullability == DfaNullability.NOT_NULL) {
        if (constraintText.isEmpty()) {
          return JavaBundle.message("dfa.constraint.not.null");
        }
        return JavaBundle.message("dfa.constraint.0.not.null", constraintText);
      }
      else if (nullability != DfaNullability.NULL) {
        if (constraintText.isEmpty()) {
          return "";
        }
        return JavaBundle.message("dfa.constraint.null.or.0", constraintText);
      }
    }
    return type.toString();
  }
}
