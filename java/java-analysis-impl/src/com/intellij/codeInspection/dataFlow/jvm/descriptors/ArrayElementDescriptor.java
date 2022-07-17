// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.DfaTypeValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import one.util.streamex.LongStreamEx;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A descriptor that represents an array element with fixed index
 */
public final class ArrayElementDescriptor extends JvmVariableDescriptor {
  private final int myIndex;

  private ArrayElementDescriptor(int index) {
    myIndex = index;
  }

  public int getIndex() {
    return myIndex;
  }

  @Override
  public @NotNull DfType getDfType(@Nullable DfaVariableValue qualifier) {
    if (qualifier == null) return DfType.TOP;
    TypeConstraint constraint = TypeConstraint.fromDfType(qualifier.getDfType());
    return constraint.getArrayComponentType();
  }

  @Override
  public @NotNull DfType getInitialDfType(@NotNull DfaVariableValue thisValue,
                                          @Nullable PsiElement context) {
    DfaVariableValue qualifier = thisValue.getQualifier();
    DfType dfType = getDfType(qualifier);
    if (qualifier != null && dfType instanceof DfReferenceType) {
      PsiVarDescriptor descriptor = ObjectUtils.tryCast(qualifier.getDescriptor(), PsiVarDescriptor.class);
      if (descriptor != null) {
        PsiType psiType = descriptor.getType(qualifier);
        if (psiType instanceof PsiArrayType) {
          PsiType componentType = ((PsiArrayType)psiType).getComponentType();
          return dfType.meet(DfaNullability.fromNullability(DfaPsiUtil.getTypeNullability(componentType)).asDfType());
        }
      }
    }
    return dfType;
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
    DfType componentType = TypeConstraint.fromDfType(arrayDfaVar.getDfType()).getArrayComponentType();
    if (componentType == DfType.BOTTOM) return null;
    PsiVariable arrayPsiVar = ObjectUtils.tryCast(arrayDfaVar.getPsiVariable(), PsiVariable.class);
    if (arrayPsiVar != null) {
      PsiExpression constantArrayElement = ExpressionUtils.getConstantArrayElement(arrayPsiVar, index);
      if (constantArrayElement != null) {
        PsiType elementType = constantArrayElement.getType();
        if (componentType instanceof DfReferenceType && elementType instanceof PsiPrimitiveType &&
            !TypeConstraint.fromDfType(componentType).isPrimitiveWrapper()) {
          componentType = DfTypes.typedObject(((PsiPrimitiveType)elementType).getBoxedType(constantArrayElement), Nullability.UNKNOWN)
            .meet(componentType);
        }
        return getAdvancedExpressionDfaValue(factory, constantArrayElement, componentType);
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
    if (array instanceof DfaTypeValue) {
      PsiVariable var = array.getDfType().getConstantOfType(PsiVariable.class);
      if (var != null) {
        array = new PlainDescriptor(var).createValue(factory, null);
      }
    }
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
    DfType arrayType = arrayDfaVar.getDfType();
    DfType targetType = TypeConstraint.fromDfType(arrayType).getArrayComponentType();
    PsiExpression[] elements = ExpressionUtils.getConstantArrayElements(arrayPsiVar);
    if (elements == null || elements.length == 0) return factory.getUnknown();
    indexSet = indexSet.meet(LongRangeSet.range(0, elements.length - 1));
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
                                                        @NotNull DfType targetType) {
    if (expression == null) return factory.getUnknown();
    DfaValue value = JavaDfaValueFactory.getExpressionDfaValue(factory, expression);
    if (value != null) {
      if (value instanceof DfaVariableValue) {
        value = factory.fromDfType(value.getDfType());
      }
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
    if (dfType instanceof DfPrimitiveType && targetType instanceof DfPrimitiveType && dfType.meet(targetType) == DfType.BOTTOM) {
      if (targetType instanceof DfIntegralType) {
        if (targetType instanceof DfLongType) return factory.fromDfType(((DfPrimitiveType)dfType).castTo(PsiType.LONG));
      }
      return factory.fromDfType(targetType);
    }
    return DfaUtil.boxUnbox(factory.fromDfType(dfType), targetType);
  }
}
