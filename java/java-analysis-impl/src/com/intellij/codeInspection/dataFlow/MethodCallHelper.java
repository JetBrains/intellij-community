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

import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiType;
import com.intellij.util.Producer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.codeInspection.dataFlow.value.DfaRelation.EQ;

public abstract class MethodCallHelper<M> {

  public LinkedHashSet<DfaMemoryState> addContractResults(final @NotNull DfaValue[] argValues,
                                                          final @NotNull MethodContract contract,
                                                          LinkedHashSet<DfaMemoryState> states,
                                                          final M instruction,
                                                          final Set<DfaMemoryState> finalStates) {
    final DfaValueFactory factory = getFactory();
    DfaConstValue.Factory constFactory = factory.getConstFactory();
    LinkedHashSet<DfaMemoryState> falseStates = ContainerUtil.newLinkedHashSet();
    for (int i = 0; i < argValues.length; i++) {
      DfaValue argValue = argValues[i];
      MethodContract.ValueConstraint constraint = contract.arguments[i];
      DfaConstValue expectedValue = constraint.getComparisonValue(factory);
      if (expectedValue == null) continue;

      boolean nullContract = expectedValue == constFactory.getNull();


      boolean invertCondition = constraint.shouldUseNonEqComparison();
      DfaValue condition = factory.getRelationFactory().createRelation(argValue, expectedValue, EQ, invertCondition);
      if (condition == null) {
        if (!(argValue instanceof DfaConstValue)) {
          for (DfaMemoryState state : states) {
            DfaMemoryState falseCopy = state.createCopy();
            if (nullContract) {
              (invertCondition ? falseCopy : state).markEphemeral();
            }
            falseStates.add(falseCopy);
          }
          continue;
        }
        condition = constFactory.createFromValue((argValue == expectedValue) != invertCondition, PsiType.BOOLEAN, null);
      }

      LinkedHashSet<DfaMemoryState> nextStates = ContainerUtil.newLinkedHashSet();
      for (DfaMemoryState state : states) {
        boolean unknownVsNull = nullContract &&
                                argValue instanceof DfaVariableValue &&
                                ((DfaMemoryStateImpl)state).getVariableState((DfaVariableValue)argValue).getNullability() ==
                                Nullness.UNKNOWN;
        DfaMemoryState falseCopy = state.createCopy();
        if (state.applyCondition(condition)) {
          if (unknownVsNull && !invertCondition) {
            state.markEphemeral();
          }
          nextStates.add(state);
        }
        if (falseCopy.applyCondition(condition.createNegated())) {
          if (unknownVsNull && invertCondition) {
            falseCopy.markEphemeral();
          }
          falseStates.add(falseCopy);
        }
      }
      states = nextStates;
    }

    for (DfaMemoryState state : states) {
      final DfaValue contractReturnValue = getDfaContractReturnValue(contract, getReturnTypeProducer(instruction), factory);
      state.push(contractReturnValue != null ? contractReturnValue : getMethodResultValue(instruction, null));
      finalStates.add(state);
    }

    return falseStates;
  }

  @Nullable
  private static DfaValue getDfaContractReturnValue(MethodContract contract,
                                                    Producer<PsiType> typeProducer,
                                                    DfaValueFactory factory) {
    switch (contract.returnValue) {
      case NULL_VALUE:
        return factory.getConstFactory().getNull();
      case NOT_NULL_VALUE:
        return factory.createTypeValue(typeProducer.produce(), Nullness.NOT_NULL);
      case TRUE_VALUE:
        return factory.getConstFactory().getTrue();
      case FALSE_VALUE:
        return factory.getConstFactory().getFalse();
      case THROW_EXCEPTION:
        return factory.getConstFactory().getContractFail();
      default:
        return null;
    }
  }


  @NotNull
  protected abstract DfaValue getMethodResultValue(M instruction, @Nullable DfaValue qualifierValue);

  @NotNull
  protected abstract Producer<PsiType> getReturnTypeProducer(M instruction);

  @NotNull
  protected abstract DfaValueFactory getFactory();
}
