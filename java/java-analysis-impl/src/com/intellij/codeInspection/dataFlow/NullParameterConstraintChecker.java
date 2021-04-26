// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.java.JavaDfaInstructionVisitor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.AssignInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.PushInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.project.Project;
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
 * 1. If at least one successful method execution ({@link ReturnInstruction#isViaException()} == false)
 * doesn't require a not-null value for the parameter ({@link DfaMemoryState#isNotNull(DfaValue) == false});
 * OR
 * 2. If the parameter has a reassignment while one of any method execution.
 *
 * All remaining violated parameters is required to be not-null for successful method execution.
 */
final class NullParameterConstraintChecker extends StandardDataFlowRunner {
  private final Set<PsiParameter> myPossiblyViolatedParameters;
  private final Set<PsiParameter> myUsedParameters;
  private final Set<PsiParameter> myParametersWithSuccessfulExecutionInNotNullState;

  private NullParameterConstraintChecker(Project project, Collection<PsiParameter> parameters) {
    super(project);
    myPossiblyViolatedParameters = new HashSet<>(parameters);
    myParametersWithSuccessfulExecutionInNotNullState = new HashSet<>();
    myUsedParameters = new HashSet<>();
  }

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

    NullParameterConstraintChecker checker = new NullParameterConstraintChecker(method.getProject(), nullableParameters);
    checker.analyzeMethod(method.getBody(), new JavaDfaInstructionVisitor(null));

    return checker.myPossiblyViolatedParameters
      .stream()
      .filter(checker.myUsedParameters::contains)
      .filter(checker.myParametersWithSuccessfulExecutionInNotNullState::contains)
      .toArray(PsiParameter[]::new);
  }

  @Override
  protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
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

    if (instruction instanceof ReturnInstruction && !((ReturnInstruction)instruction).isViaException()) {
      DfaMemoryState memState = instructionState.getMemoryState();
      for (PsiParameter parameter : myPossiblyViolatedParameters.toArray(PsiParameter.EMPTY_ARRAY)) {
        final DfaVariableValue dfaVar = PlainDescriptor.createVariableValue(getFactory(), parameter);
        if (memState.isNotNull(dfaVar)) {
          myParametersWithSuccessfulExecutionInNotNullState.add(parameter);
        }
        else {
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
      for (PsiParameter parameter : myPossiblyViolatedParameters) {
        recordVariableType(PlainDescriptor.createVariableValue(getFactory(), parameter),
                           DfaNullability.NULLABLE.asDfType());
      }
    }

    protected MyDfaMemoryState(MyDfaMemoryState toCopy) {
      super(toCopy);
    }

    @Override
    protected void flushVariable(@NotNull DfaVariableValue variable, boolean canonicalize, boolean shouldMarkFlushed) {
      final PsiElement psi = variable.getPsiVariable();
      if (psi instanceof PsiParameter && myPossiblyViolatedParameters.contains(psi)) return;
      super.flushVariable(variable, canonicalize, shouldMarkFlushed);
    }

    @NotNull
    @Override
    public DfaMemoryStateImpl createCopy() {
      return new MyDfaMemoryState(this);
    }
  }
}
