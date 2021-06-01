// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.interpreter.StandardDataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.ControlFlowAnalyzer;
import com.intellij.codeInspection.dataFlow.java.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.jvm.JvmDfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.*;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryStateImpl;
import com.intellij.codeInspection.dataFlow.types.DfTypes;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.impl.search.JavaNullMethodArgumentUtil;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * This checker uses following idea:
 * On the checker's start mark all parameters with null-argument usages as violated (i.e. the method fails if parameter is null).
 * A parameter can be amnestied (excluded from violated) when one of following statements is true:
 * 1. If at least one successful method execution ({@link ReturnInstruction} visited)
 * doesn't require a not-null value for the parameter
 * OR
 * 2. If the parameter has a reassignment while one of any method execution.
 *
 * All remaining violated parameters is required to be not-null for successful method execution.
 */
final class NullParameterConstraintChecker {
  static PsiParameter @NotNull [] checkMethodParameters(PsiMethod method) {
    if (method.getBody() == null) return PsiParameter.EMPTY_ARRAY;

    final Collection<PsiParameter> nullableParameters = new SmartList<>();
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int index = 0; index < parameters.length; index++) {
      PsiParameter parameter = parameters[index];
      if (!(parameter.getType() instanceof PsiPrimitiveType) &&
          NullableNotNullManager.getNullability(parameter) == Nullability.UNKNOWN &&
          JavaNullMethodArgumentUtil.hasNullArgument(method, index)) {
        nullableParameters.add(parameter);
      }
    }
    if (nullableParameters.isEmpty()) return PsiParameter.EMPTY_ARRAY;

    DfaValueFactory factory = new DfaValueFactory(method.getProject());
    ControlFlow flow = ControlFlowAnalyzer.buildFlow(method.getBody(), factory, true);
    if (flow == null) return PsiParameter.EMPTY_ARRAY;
    var interpreter = new NullParameterCheckerInterpreter(flow, nullableParameters);
    interpreter.interpret(new MyDfaMemoryState(factory, interpreter.myPossiblyViolatedParameters));

    return interpreter.myPossiblyViolatedParameters
      .stream()
      .filter(interpreter.myUsedParameters::contains)
      .filter(interpreter.myParametersWithSuccessfulExecutionInNotNullState::contains)
      .toArray(PsiParameter[]::new);
  }

  private static class MyDfaMemoryState extends JvmDfaMemoryStateImpl {
    final Set<PsiParameter> myPossiblyViolatedParameters;

    protected MyDfaMemoryState(DfaValueFactory factory, Set<PsiParameter> possiblyViolatedParameters) {
      super(factory);
      myPossiblyViolatedParameters = possiblyViolatedParameters;
      for (PsiParameter parameter : myPossiblyViolatedParameters) {
        recordVariableType(PlainDescriptor.createVariableValue(getFactory(), parameter),
                           DfaNullability.NULLABLE.asDfType());
      }
    }

    protected MyDfaMemoryState(MyDfaMemoryState toCopy) {
      super(toCopy);
      myPossiblyViolatedParameters = toCopy.myPossiblyViolatedParameters;
    }

    @Override
    public void flushVariable(@NotNull DfaVariableValue variable, boolean canonicalize) {
      final PsiElement psi = variable.getPsiVariable();
      if (psi instanceof PsiParameter && myPossiblyViolatedParameters.contains(psi)) return;
      super.flushVariable(variable, canonicalize);
    }

    @NotNull
    @Override
    public DfaMemoryStateImpl createCopy() {
      return new MyDfaMemoryState(this);
    }
  }

  private static class NullParameterCheckerInterpreter extends StandardDataFlowInterpreter {
    final Set<PsiParameter> myPossiblyViolatedParameters;
    final Set<PsiParameter> myUsedParameters;
    final Set<PsiParameter> myParametersWithSuccessfulExecutionInNotNullState;

    private NullParameterCheckerInterpreter(ControlFlow flow, Collection<PsiParameter> nullableParameters) {
      super(flow, DfaListener.EMPTY);
      myPossiblyViolatedParameters = new HashSet<>(nullableParameters);
      myUsedParameters = new HashSet<>();
      myParametersWithSuccessfulExecutionInNotNullState = new HashSet<>();
    }

    @Override
    protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
      Instruction instruction = instructionState.getInstruction();
      if (instruction instanceof PushInstruction) {
        final DfaValue var = ((PushInstruction)instruction).getValue();
        if (var instanceof DfaVariableValue) {
          final PsiElement psiVar = ((DfaVariableValue)var).getPsiVariable();
          if (psiVar instanceof PsiParameter) {
            myUsedParameters.add((PsiParameter)psiVar);
          }
        }
      }

      if (instruction instanceof AssignInstruction) {
        final DfaValue value = ((AssignInstruction)instruction).getAssignedValue();
        if (value instanceof DfaVariableValue) {
          final PsiElement psiVariable = ((DfaVariableValue)value).getPsiVariable();
          if (psiVariable instanceof PsiParameter) {
            myPossiblyViolatedParameters.remove(psiVariable);
          }
        }
      }

      if (instruction instanceof ReturnInstruction) {
        DfaMemoryState memState = instructionState.getMemoryState();
        for (PsiParameter parameter : myPossiblyViolatedParameters.toArray(PsiParameter.EMPTY_ARRAY)) {
          final DfaVariableValue dfaVar = PlainDescriptor.createVariableValue(getFactory(), parameter);
          if (!memState.getDfType(dfaVar).isSuperType(DfTypes.NULL)) {
            myParametersWithSuccessfulExecutionInNotNullState.add(parameter);
          }
          else {
            myPossiblyViolatedParameters.remove(parameter);
          }
        }
      }

      return super.acceptInstruction(instructionState);
    }
  }
}
