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
    if (!(element instanceof PsiExpression)) return true;
    PsiExpression expression = (PsiExpression)element;
    DfType dfType;
    PsiType expressionType = expression.getType();
    if (TypeConversionUtil.isPrimitiveAndNotNull(expressionType) && myDfType instanceof DfReferenceType) {
      dfType = DfTypes.typedObject(((PsiPrimitiveType)expressionType).getBoxedType(expression), Nullability.NOT_NULL);
    } else if (!(expressionType instanceof PsiPrimitiveType) && myDfType instanceof DfPrimitiveType) {
      dfType = DfTypes.typedObject(PsiPrimitiveType.getUnboxedType(expressionType), Nullability.NOT_NULL); 
    } else {
      dfType = CommonDataflow.getDfType(expression);
    }
    return dfType.meet(myDfType) != DfTypes.BOTTOM;
  }

  @Override
  public @NotNull String toString() {
    if (myDfType == DfTypes.TOP) {
      return InspectionGadgetsBundle.message("none");
    }
    return myDfType.toString();
  }
}
