// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class JavaDfaSliceValueFilter implements SliceValueFilter {
  private final @Nullable JavaDfaSliceValueFilter myNextFilter;
  private final @NotNull DfType myDfType;

  private JavaDfaSliceValueFilter(@Nullable JavaDfaSliceValueFilter nextFilter, @NotNull DfType type) {
    myNextFilter = nextFilter;
    myDfType = type;
  }
  
  public JavaDfaSliceValueFilter(@NotNull DfType type) {
    this(null, type);
  }
  
  JavaDfaSliceValueFilter wrap() {
    return new JavaDfaSliceValueFilter(this, DfTypes.TOP);
  }
  
  JavaDfaSliceValueFilter unwrap() {
    return myNextFilter;
  }

  @Override
  public boolean allowed(@NotNull PsiElement element) {
    if (myDfType instanceof DfConstantType && element instanceof PsiLiteralValue) {
      Object constValue = ((DfConstantType<?>)myDfType).getValue();
      if (!(constValue instanceof PsiElement)) {
        Object value = ((PsiLiteralValue)element).getValue();
        return Objects.equals(value, constValue);
      }
    }
    DfType dfType = getElementDfType(element);
    return dfType.meet(myDfType) != DfTypes.BOTTOM;
  }

  @Nullable JavaDfaSliceValueFilter mergeFilter(@NotNull PsiElement element) {
    DfType type = getElementDfType(element);
    if (type instanceof DfReferenceType) {
      type = ((DfReferenceType)type).dropLocality().dropMutability();
    }
    DfType meet = type.meet(myDfType);
    if (meet == DfTypes.TOP && myNextFilter == null) return null;
    if (meet == DfTypes.BOTTOM || meet.equals(myDfType)) return this;
    return new JavaDfaSliceValueFilter(myNextFilter, meet);
  }

  private @NotNull DfType getElementDfType(@NotNull PsiElement element) {
    if (!(element instanceof PsiExpression)) return DfTypes.TOP;
    PsiExpression expression = (PsiExpression)element;
    PsiType expressionType = expression.getType();
    if (TypeConversionUtil.isPrimitiveAndNotNull(expressionType) && myDfType instanceof DfReferenceType) {
      return DfTypes.typedObject(((PsiPrimitiveType)expressionType).getBoxedType(expression), Nullability.NOT_NULL);
    }
    if (!(expressionType instanceof PsiPrimitiveType) && myDfType instanceof DfPrimitiveType) {
      return DfTypes.typedObject(PsiPrimitiveType.getUnboxedType(expressionType), Nullability.NOT_NULL);
    }
    return CommonDataflow.getDfType(expression);
  }

  @Override
  public @NotNull String toString() {
    if (myDfType == DfTypes.TOP) {
      return InspectionGadgetsBundle.message("none");
    }
    return myDfType.toString();
  }
}
