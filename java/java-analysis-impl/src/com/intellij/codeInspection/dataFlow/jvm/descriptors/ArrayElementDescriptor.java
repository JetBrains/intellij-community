// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.DfaUtil;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.codeInspection.dataFlow.SpecialField;
import com.intellij.codeInspection.dataFlow.java.DfaExpressionFactory;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfLongType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.LongStreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.rangeClamped;

/**
 * A descriptor that represents an array element with fixed index
 */
public final class ArrayElementDescriptor implements VariableDescriptor {
  private final int myIndex;

  public ArrayElementDescriptor(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  @Nullable
  @Override
  public PsiType getType(@Nullable DfaVariableValue qualifier) {
    if (qualifier == null) return null;
    PsiType qualifierType = qualifier.getType();
    return qualifierType instanceof PsiArrayType ? ((PsiArrayType)qualifierType).getComponentType() : null;
  }

  @NotNull
  @Override
  public String toString() {
    return "[" + myIndex + "]";
  }

  @Override
  public boolean isStable() {
    return false;
  }

  @Override
  public int hashCode() {
    return myIndex + 1234567;
  }

  @Override
  public boolean equals(Object obj) {
    return obj == this ||
           obj instanceof ArrayElementDescriptor && ((ArrayElementDescriptor)obj).myIndex == myIndex;
  }

  /**
   * Get DfaValue that represents the array element with given index
   * @param factory factory to use
   * @param array array value
   * @param index array element index
   * @return array element value, or null if cannot be expressed
   */
  @Contract("_, null, _ -> null")
  public static @Nullable DfaValue getArrayElementValue(@NotNull DfaValueFactory factory, @Nullable DfaValue array, int index) {
    if (!(array instanceof DfaVariableValue) || index < 0) return null;
    DfaVariableValue arrayDfaVar = (DfaVariableValue)array;
    PsiType type = arrayDfaVar.getType();
    if (!(type instanceof PsiArrayType)) return null;
    PsiVariable arrayPsiVar = ObjectUtils.tryCast(arrayDfaVar.getPsiVariable(), PsiVariable.class);
    if (arrayPsiVar != null) {
      PsiExpression constantArrayElement = ExpressionUtils.getConstantArrayElement(arrayPsiVar, index);
      if (constantArrayElement != null) {
        return getAdvancedExpressionDfaValue(factory, constantArrayElement, ((PsiArrayType)type).getComponentType());
      }
    }
    return new ArrayElementDescriptor(index).createValue(factory, arrayDfaVar);
  }

  /**
   * Get DfaValue that represents any array element with the index from the supplied set
   * @param factory factory to use
   * @param array array value
   * @param indexSet array element indexes
   * @return array element value
   */
  public static @NotNull DfaValue getArrayElementValue(@NotNull DfaValueFactory factory,
                                                       @Nullable DfaValue array,
                                                       @NotNull LongRangeSet indexSet) {
    if (!(array instanceof DfaVariableValue)) return factory.getUnknown();
    if (indexSet.isEmpty()) return factory.getUnknown();
    long min = indexSet.min();
    long max = indexSet.max();
    if (min == max && min >= 0 && min < Integer.MAX_VALUE) {
      DfaValue value = getArrayElementValue(factory, array, (int)min);
      return value == null ? factory.getUnknown() : value;
    }
    DfaVariableValue arrayDfaVar = (DfaVariableValue)array;
    PsiVariable arrayPsiVar = ObjectUtils.tryCast(arrayDfaVar.getPsiVariable(), PsiVariable.class);
    if (arrayPsiVar == null) return factory.getUnknown();
    PsiType arrayType = arrayPsiVar.getType();
    PsiType targetType = arrayType instanceof PsiArrayType ? ((PsiArrayType)arrayType).getComponentType() : null;
    PsiExpression[] elements = ExpressionUtils.getConstantArrayElements(arrayPsiVar);
    if (elements == null || elements.length == 0) return factory.getUnknown();
    indexSet = indexSet.intersect(LongRangeSet.range(0, elements.length - 1));
    if (indexSet.isEmpty() || indexSet.isCardinalityBigger(100)) return factory.getUnknown();
    return LongStreamEx.of(indexSet.stream())
      .mapToObj(idx -> getAdvancedExpressionDfaValue(factory, elements[(int)idx], targetType))
      .prefix(DfaValue::unite)
      .takeWhileInclusive(value -> !DfaTypeValue.isUnknown(value))
      .reduce((a, b) -> b)
      .orElseGet(factory::getUnknown);
  }

  @NotNull
  private static DfaValue getAdvancedExpressionDfaValue(@NotNull DfaValueFactory factory,
                                                        @Nullable PsiExpression expression,
                                                        @Nullable PsiType targetType) {
    if (expression == null) return factory.getUnknown();
    DfaValue value = DfaExpressionFactory.getExpressionDfaValue(factory, expression);
    if (value != null) {
      return DfaUtil.boxUnbox(value, targetType);
    }
    if (expression instanceof PsiConditionalExpression) {
      return getAdvancedExpressionDfaValue(factory, ((PsiConditionalExpression)expression).getThenExpression(), targetType).unite(
        getAdvancedExpressionDfaValue(factory, ((PsiConditionalExpression)expression).getElseExpression(), targetType));
    }
    PsiType type = expression.getType();
    if (expression instanceof PsiArrayInitializerExpression) {
      int length = ((PsiArrayInitializerExpression)expression).getInitializers().length;
      return factory.fromDfType(SpecialField.ARRAY_LENGTH.asDfType(DfTypes.intValue(length))
                                  .meet(DfTypes.typedObject(type, Nullability.NOT_NULL)));
    }
    DfType dfType = DfTypes.typedObject(type, NullabilityUtil.getExpressionNullability(expression));
    if (type instanceof PsiPrimitiveType && targetType instanceof PsiPrimitiveType && !type.equals(targetType)) {
      if (TypeConversionUtil.isIntegralNumberType(targetType)) {
        LongRangeSet range = DfLongType.extractRange(dfType);
        return factory.fromDfType(rangeClamped(range.castTo((PsiPrimitiveType)targetType), PsiType.LONG.equals(targetType)));
      }
      return factory.fromDfType(DfTypes.typedObject(targetType, Nullability.UNKNOWN));
    }
    return DfaUtil.boxUnbox(factory.fromDfType(dfType), targetType);
  }
}
