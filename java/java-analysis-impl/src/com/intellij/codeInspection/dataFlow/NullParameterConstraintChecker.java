/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.instructions.AssignInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.util.SmartList;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Set;

/**
 * This checker uses following idea:
 * On the checker's start mark all parameters with null-argument usages as violated (i.e. the method fails if parameter is null).
 * A parameter can be amnestied (excluded from violated) when one of following statements is true:
 * 1. If at least one successful method execution ({@link ReturnInstruction#isViaException()} == false)
 * doesn't require a not-null value for the parameter ({@link DfaMemoryState#isNotNull(DfaValue) == false});
 * OR
 * 2. If the parameter has a reassignment while one of any method execution.
 *
 * All remaining violated parameters is required to be not-null for successful method execution.
 */
class NullParameterConstraintChecker extends DataFlowRunner {
  private final Set<PsiParameter> myPossiblyViolatedParameters;

  private NullParameterConstraintChecker(Collection<PsiParameter> parameters, boolean isOnTheFly) {
    super(false, true, isOnTheFly);
    myPossiblyViolatedParameters = new THashSet<>(parameters);
  }

  @NotNull
  static PsiParameter[] checkMethodParameters(PsiMethod method, boolean isOnTheFly) {
    if (method.getBody() == null) return PsiParameter.EMPTY_ARRAY;

    final Collection<PsiParameter> nullableParameters = new SmartList<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int index = 0; index < parameters.length; index++) {
      PsiParameter parameter = parameters[index];
      if (!(parameter.getType() instanceof PsiPrimitiveType) &&
          !NullableNotNullManager.isNotNull(parameter) &&
          !NullableNotNullManager.isNullable(parameter) &&
          JavaNullMethodArgumentUtil.hasNullArgument(method, index)) {
        nullableParameters.add(parameter);
      }
    }
    if (nullableParameters.isEmpty()) return PsiParameter.EMPTY_ARRAY;

    final NullParameterConstraintChecker checker = new NullParameterConstraintChecker(nullableParameters, isOnTheFly);
    checker.analyzeMethod(method.getBody(), new StandardInstructionVisitor());

    return checker.myPossiblyViolatedParameters.toArray(new PsiParameter[checker.myPossiblyViolatedParameters.size()]);
  }

  @NotNull
  @Override
  protected DfaInstructionState[] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
    DfaMemoryState memState = instructionState.getMemoryState();
    if (memState.isEphemeral()) {
      return DfaInstructionState.EMPTY_ARRAY;
    }
    Instruction instruction = instructionState.getInstruction();

    if (instruction instanceof AssignInstruction) {
      final DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
      if (value instanceof DfaVariableValue) {
        final PsiModifierListOwner psiVariable = ((DfaVariableValue)value).getPsiVariable();
        if (psiVariable instanceof PsiParameter) {
          myPossiblyViolatedParameters.remove(psiVariable);
        }
      }
    }

    if (instruction instanceof ReturnInstruction && !((ReturnInstruction)instruction).isViaException()) {
      for (PsiParameter parameter : myPossiblyViolatedParameters.toArray(new PsiParameter[myPossiblyViolatedParameters.size()])) {
        final DfaVariableValue dfaVar = getFactory().getVarFactory().createVariableValue(parameter, false);
        if (!memState.isNotNull(dfaVar)) {
          myPossiblyViolatedParameters.remove(parameter);
        }
      }
    }

    return super.acceptInstruction(visitor, instructionState);
  }

  @NotNull
  @Override
  protected DfaMemoryState createMemoryState() {
    return new MyDfaMemoryState(getFactory());
  }

  private class MyDfaMemoryState extends DfaMemoryStateImpl {

    protected MyDfaMemoryState(DfaValueFactory factory) {
      super(factory);
    }

    protected MyDfaMemoryState(MyDfaMemoryState toCopy) {
      super(toCopy);
    }

    @Override
    public void flushVariable(@NotNull DfaVariableValue variable) {
      final PsiModifierListOwner psi = variable.getPsiVariable();
      if (psi instanceof PsiParameter && myPossiblyViolatedParameters.contains(psi)) return;
      super.flushVariable(variable);
    }

    @NotNull
    @Override
    public DfaMemoryStateImpl createCopy() {
      return new MyDfaMemoryState(this);
    }
  }
}
