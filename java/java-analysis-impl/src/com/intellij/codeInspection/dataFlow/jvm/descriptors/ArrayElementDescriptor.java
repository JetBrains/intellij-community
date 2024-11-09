// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.jvm.descriptors;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.*;
import com.intellij.codeInspection.dataFlow.java.JavaDfaValueFactory;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.*;
import com.intellij.codeInspection.dataFlow.value.*;
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

  /**
   * Creates a descriptor that represents an array element with a fixed index
   * 
   * @param index index of an array element
   */
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
      VariableDescriptor qualDescriptor = qualifier.getDescriptor();
      int depth = 1;
      while (qualDescriptor instanceof ArrayElementDescriptor) {
        depth++;
        qualifier = qualifier.getQualifier();
        if (qualifier == null) break;
        qualDescriptor = qualifier.getDescriptor();
      }
      PsiVarDescriptor descriptor = ObjectUtils.tryCast(qualDescriptor, PsiVarDescriptor.class);
      if (descriptor != null) {
        PsiType psiType = descriptor.getType(qualifier);
        for (int i = 0; i < depth; i++) {
          if (!(psiType instanceof PsiArrayType arrayType)) return dfType;
          psiType = arrayType.getComponentType();
        }
        return dfType.meet(DfaNullability.fromNullability(DfaPsiUtil.getTypeNullability(psiType)).asDfType());
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
    if (!(array instanceof DfaVariableValue arrayDfaVar) || index < 0) return null;
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
    if (!(array instanceof DfaVariableValue arrayDfaVar)) return factory.getUnknown();
    if (indexSet.isEmpty()) return factory.getUnknown();
    long min = indexSet.min();
    long max = indexSet.max();
    if (min == max && min >= 0 && min < Integer.MAX_VALUE) {
      DfaValue value = getArrayElementValue(factory, array, (int)min);
      return value == null ? factory.getUnknown() : value;
    }
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
        if (targetType instanceof DfLongType) return factory.fromDfType(((DfPrimitiveType)dfType).castTo(PsiTypes.longType()));
      }
      return factory.fromDfType(targetType);
    }
    return DfaUtil.boxUnbox(factory.fromDfType(dfType), targetType);
  }

  /**
   * @param array array value
   * @return inherent type of array component
   */
  public static @NotNull DfType getArrayComponentType(@NotNull DfaValue array) {
    DfType componentType = TypeConstraint.fromDfType(array.getDfType()).getArrayComponentType();
    if (componentType instanceof DfReferenceType && 
        array instanceof DfaVariableValue var && var.getDescriptor() instanceof PsiVarDescriptor varDescriptor &&
        varDescriptor.getType(var.getQualifier()) instanceof PsiArrayType arrayType) {
      return componentType.meet(DfaNullability.fromNullability(DfaPsiUtil.getTypeNullability(arrayType.getComponentType())).asDfType());
    }
    return componentType;
  }

  /**
   * @param arrayAccess expression to create a descriptor for
   * @return an array element descriptor that describes a specified array access expression;
   * null if it's not possible to describe a given array access expression with a single
   * {@code ArrayElementDescriptor}
   */
  public static @Nullable ArrayElementDescriptor fromArrayAccess(@NotNull PsiArrayAccessExpression arrayAccess) {
    return ExpressionUtils.computeConstantExpression(arrayAccess.getIndexExpression()) instanceof Integer index ?
         new ArrayElementDescriptor(index) : null;
  }
}
