/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Producer;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaMethodCallHelper extends MethodCallHelper<MethodCallInstruction> {

  private final DfaValueFactory myFactory;

  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  private final FactoryMap<MethodCallInstruction, Nullness> myReturnTypeNullability =
    new FactoryMap<MethodCallInstruction, Nullness>() {
      @Override
      protected Nullness create(MethodCallInstruction key) {
        final PsiCallExpression callExpression = key.getCallExpression();
        if (callExpression instanceof PsiNewExpression) {
          return Nullness.NOT_NULL;
        }
        return callExpression != null ? DfaPsiUtil.getElementNullability(key.getResultType(), callExpression.resolveMethod()) : null;
      }
    };

  public JavaMethodCallHelper(DfaValueFactory factory) {
    myFactory = factory;
  }

  @Override
  @NotNull
  protected DfaValue getMethodResultValue(@NotNull MethodCallInstruction instruction, @Nullable DfaValue qualifierValue) {
    final DfaValueFactory factory = getFactory();
    DfaValue precalculated = instruction.getPrecalculatedReturnValue();
    if (precalculated != null) {
      return precalculated;
    }

    final PsiType type = instruction.getResultType();
    final MethodCallInstruction.MethodType methodType = instruction.getMethodType();

    if (methodType == MethodCallInstruction.MethodType.UNBOXING) {
      return factory.getBoxedFactory().createUnboxed(qualifierValue);
    }

    if (methodType == MethodCallInstruction.MethodType.BOXING) {
      DfaValue boxed = factory.getBoxedFactory().createBoxed(qualifierValue);
      return boxed == null ? factory.createTypeValue(type, Nullness.NOT_NULL) : boxed;
    }

    if (methodType == MethodCallInstruction.MethodType.CAST) {
      assert qualifierValue != null;
      if (qualifierValue instanceof DfaConstValue) {
        Object casted = TypeConversionUtil.computeCastTo(((DfaConstValue)qualifierValue).getValue(), type);
        return factory.getConstFactory().createFromValue(casted, type, ((DfaConstValue)qualifierValue).getConstant());
      }
      return qualifierValue;
    }

    if (type != null && (type instanceof PsiClassType || type.getArrayDimensions() > 0)) {
      Nullness nullability = myReturnTypeNullability.get(instruction);
      if (nullability == Nullness.UNKNOWN && factory.isUnknownMembersAreNullable()) {
        nullability = Nullness.NULLABLE;
      }
      return factory.createTypeValue(type, nullability);
    }
    return DfaUnknownValue.getInstance();
  }

  @NotNull
  @Override
  protected Producer<PsiType> getReturnTypeProducer(final @NotNull MethodCallInstruction instruction) {
    return new Producer<PsiType>() {
      @Nullable
      @Override
      public PsiType produce() {
        return instruction.getResultType();
      }
    };
  }

  @NotNull
  @Override
  protected DfaValueFactory getFactory() {
    return myFactory;
  }
}
