// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.instructions.CheckReturnValueInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue.RelationType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
* @author peter
*/
class ContractChecker extends DataFlowRunner {
  private final PsiMethod myMethod;
  private final StandardMethodContract myContract;
  private final boolean myOwnContract;
  private final Set<PsiElement> myViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myNonViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myFailures = ContainerUtil.newHashSet();
  private boolean myMayReturnNormally = false;

  private ContractChecker(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
    super(false, null);
    myMethod = method;
    myContract = contract;
    myOwnContract = ownContract;
  }

  static Map<PsiElement, String> checkContractClause(PsiMethod method, StandardMethodContract contract, boolean ownContract) {

    PsiCodeBlock body = method.getBody();
    if (body == null) return Collections.emptyMap();

    ContractChecker checker = new ContractChecker(method, contract, ownContract);

    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = checker.createMemoryState();
    final DfaValueFactory factory = checker.getFactory();
    for (int i = 0; i < contract.getParameterCount(); i++) {
      ValueConstraint constraint = contract.getParameterConstraint(i);
      DfaConstValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = factory.getVarFactory().createVariableValue(parameters[i]);
        initialState.applyCondition(factory.createCondition(dfaParam, RelationType.equivalence(!negated), comparisonValue));
      }
    }

    checker.analyzeMethod(body, new StandardInstructionVisitor(), false, Collections.singletonList(initialState));
    return checker.getErrors();
  }

  @NotNull
  @Override
  protected DfaInstructionState[] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
    DfaMemoryState memState = instructionState.getMemoryState();
    if (memState.isEphemeral()) {
      return super.acceptInstruction(visitor, instructionState);
    }
    Instruction instruction = instructionState.getInstruction();
    if (instruction instanceof CheckReturnValueInstruction) {
      PsiElement anchor = ((CheckReturnValueInstruction)instruction).getReturn();
      DfaValue retValue = memState.pop();
      if (!myContract.getReturnValue().isValueCompatible(memState, retValue)) {
        myViolations.add(anchor);
      } else {
        myNonViolations.add(anchor);
      }
      return InstructionVisitor.nextInstruction(instruction, this, memState);

    }

    if (instruction instanceof ReturnInstruction) {
      if (((ReturnInstruction)instruction).isViaException()) {
        ContainerUtil.addIfNotNull(myFailures, ((ReturnInstruction)instruction).getAnchor());
      } else {
        myMayReturnNormally = true;
      }
    }

    if (instruction instanceof MethodCallInstruction &&
        ((MethodCallInstruction)instruction).getMethodType() == MethodCallInstruction.MethodType.REGULAR_METHOD_CALL) {
      if (myContract.getReturnValue().isFail()) {
        ContainerUtil.addIfNotNull(myFailures, ((MethodCallInstruction)instruction).getCallExpression());
        return DfaInstructionState.EMPTY_ARRAY;
      }
      if (weCannotInferAnythingAboutMethodReturnValue((MethodCallInstruction)instruction)) {
        return markEverythingEphemeral(visitor, instructionState);
      }
    }

    return super.acceptInstruction(visitor, instructionState);
  }

  private static boolean weCannotInferAnythingAboutMethodReturnValue(MethodCallInstruction instruction) {
    PsiMethod target = instruction.getTargetMethod();
    return instruction.getContracts().isEmpty() && target != null && !target.isConstructor() && !NullableNotNullManager.isNotNull(target);
  }

  @NotNull
  private DfaInstructionState[] markEverythingEphemeral(@NotNull InstructionVisitor visitor,
                                                        @NotNull DfaInstructionState instructionState) {
    DfaInstructionState[] result = super.acceptInstruction(visitor, instructionState);
    for (DfaInstructionState state : result) {
      state.getMemoryState().markEphemeral();
    }
    return result;
  }

  private Map<PsiElement, String> getErrors() {
    HashMap<PsiElement, String> errors = ContainerUtil.newHashMap();
    for (PsiElement element : myViolations) {
      if (!myNonViolations.contains(element)) {
        errors.put(element, "Contract clause '" + myContract + "' is violated");
      }
    }

    if (!myContract.getReturnValue().isFail()) {
      if (myOwnContract && !myMayReturnNormally &&
          !(PsiUtil.canBeOverridden(myMethod) && ControlFlowUtils.methodAlwaysThrowsException(myMethod))) {
        for (PsiElement element : myFailures) {
          errors.put(element, "Return value of clause '" + myContract + "' could be replaced with 'fail' as method always fails"+
                              (myContract.isTrivial() ? "" : " in this case"));
        }
      }
    } else if (myFailures.isEmpty() && errors.isEmpty()) {
      PsiIdentifier nameIdentifier = myMethod.getNameIdentifier();
      errors.put(nameIdentifier != null ? nameIdentifier : myMethod,
                 "Contract clause '" + myContract + "' is violated: no exception is thrown");
    }

    return errors;
  }
}
