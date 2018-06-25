// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.instructions.ControlTransferInstruction;
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
class ContractChecker {
  private static class ContractCheckerVisitor extends StandardInstructionVisitor {
    private final PsiMethod myMethod;
    private final StandardMethodContract myContract;
    private final boolean myOwnContract;
    private final Set<PsiElement> myViolations = ContainerUtil.newHashSet();
    private final Set<PsiElement> myNonViolations = ContainerUtil.newHashSet();
    private final Set<PsiElement> myFailures = ContainerUtil.newHashSet();
    private boolean myMayReturnNormally = false;

    ContractCheckerVisitor(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
      myMethod = method;
      myContract = contract;
      myOwnContract = ownContract;
    }

    @Override
    protected void checkReturnValue(@NotNull DfaValue value,
                                    @NotNull PsiExpression expression,
                                    @NotNull PsiParameterListOwner context,
                                    @NotNull DfaMemoryState state) {
      if (context != myMethod || state.isEphemeral()) return;
      if (!myContract.getReturnValue().isValueCompatible(state, value)) {
        myViolations.add(expression);
      } else {
        myNonViolations.add(expression);
      }
    }

    @Override
    public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction,
                                                 DataFlowRunner runner,
                                                 DfaMemoryState memState) {
      if (!memState.isEphemeral() && instruction.getMethodType() == MethodCallInstruction.MethodType.REGULAR_METHOD_CALL) {
        if (myContract.getReturnValue().isFail()) {
          ContainerUtil.addIfNotNull(myFailures, instruction.getCallExpression());
          return DfaInstructionState.EMPTY_ARRAY;
        }
        if (weCannotInferAnythingAboutMethodReturnValue(instruction)) {
          DfaInstructionState[] states = super.visitMethodCall(instruction, runner, memState);
          for (DfaInstructionState state: states) {
            state.getMemoryState().markEphemeral();
          }
          return states;
        }
      }
      return super.visitMethodCall(instruction, runner, memState);
    }

    @NotNull
    @Override
    public DfaInstructionState[] visitControlTransfer(@NotNull ControlTransferInstruction instruction,
                                                      @NotNull DataFlowRunner runner,
                                                      @NotNull DfaMemoryState state) {
      if (!state.isEphemeral()) {
        if (instruction instanceof ReturnInstruction && ((ReturnInstruction)instruction).isViaException()) {
          ContainerUtil.addIfNotNull(myFailures, ((ReturnInstruction)instruction).getAnchor());
        }
        else {
          myMayReturnNormally = true;
        }
      }
      return super.visitControlTransfer(instruction, runner, state);
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

    private static boolean weCannotInferAnythingAboutMethodReturnValue(MethodCallInstruction instruction) {
      PsiMethod target = instruction.getTargetMethod();
      return instruction.getContracts().isEmpty() && target != null && !target.isConstructor() && !NullableNotNullManager.isNotNull(target);
    }
  }

  static Map<PsiElement, String> checkContractClause(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return Collections.emptyMap();

    DataFlowRunner runner = new StandardDataFlowRunner(false, null);

    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = runner.createMemoryState();
    final DfaValueFactory factory = runner.getFactory();
    for (int i = 0; i < contract.getParameterCount(); i++) {
      ValueConstraint constraint = contract.getParameterConstraint(i);
      DfaConstValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = factory.getVarFactory().createVariableValue(parameters[i]);
        initialState.applyCondition(factory.createCondition(dfaParam, RelationType.equivalence(!negated), comparisonValue));
      }
    }

    ContractCheckerVisitor visitor = new ContractCheckerVisitor(method, contract, ownContract);
    runner.analyzeMethod(body, visitor, false, Collections.singletonList(initialState));
    return visitor.getErrors();
  }
}
