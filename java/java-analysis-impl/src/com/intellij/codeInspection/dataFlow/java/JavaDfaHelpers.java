// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfPrimitiveType;
import com.intellij.codeInspection.dataFlow.types.DfReferenceType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.DfaWrappedValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.function.UnaryOperator;

/**
 * Utility class to help interpreting the Java DFA
 */
public final class JavaDfaHelpers {
  public static DfaValue dropLocality(DfaValue value, DfaMemoryState state) {
    if (!(value instanceof DfaVariableValue var)) {
      DfType type = value.getDfType();
      if (type.isLocal() && type instanceof DfReferenceType) {
        DfReferenceType dfType = ((DfReferenceType)type).dropLocality();
        if (value instanceof DfaWrappedValue) {
          return value.getFactory().getWrapperFactory()
            .createWrapper(dfType, ((DfaWrappedValue)value).getSpecialField(), ((DfaWrappedValue)value).getWrappedValue());
        }
        return value.getFactory().fromDfType(dfType);
      }
      return value;
    }
    UnaryOperator<@NotNull DfType> updater = dfType -> dfType instanceof DfReferenceType refType ? refType.dropLocality() : dfType;
    state.updateDfType(var, updater);
    for (DfaVariableValue v : new ArrayList<>(var.getDependentVariables())) {
      state.updateDfType(v, updater);
    }
    return value;
  }

  public static boolean mayLeakFromType(@NotNull DfType type) {
    if (type == DfType.BOTTOM) return false;
    // Complex value from field or method return call may contain back-reference to the object, so
    // local value could leak. Do not drop locality only for some simple values.
    while (true) {
      TypeConstraint constraint = TypeConstraint.fromDfType(type);
      DfType arrayComponentType = constraint.getArrayComponentType();
      if (arrayComponentType == DfType.BOTTOM) {
        return !(type instanceof DfPrimitiveType) && !constraint.isExact(CommonClassNames.JAVA_LANG_STRING) &&
               !constraint.isExact(CommonClassNames.JAVA_LANG_CLASS);
      }
      type = arrayComponentType;
    }
  }

  /**
   * @param expression functional expression
   * @return DfType that describes the type of supplied expression as precise as possible
   */
  public static @NotNull DfType getFunctionDfType(@NotNull PsiFunctionalExpression expression) {
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(expression.getParent());
    PsiType psiType;
    if (parent instanceof PsiTypeCastExpression) {
      psiType = ((PsiTypeCastExpression)parent).getType();
    }
    else {
      psiType = expression.getFunctionalInterfaceType();
    }
    return (psiType == null ? DfType.TOP : TypeConstraints.exactSubtype(expression, psiType).asDfType()).meet(DfTypes.NOT_NULL_OBJECT);
  }

  static boolean mayLeakFromExpression(@NotNull PsiExpression expression) {
    PsiElement parent = ExpressionUtils.getPassThroughParent(expression);
    return !(parent instanceof PsiInstanceOfExpression) && !(parent instanceof PsiPolyadicExpression);
  }
}
