// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.instructions.ControlTransferInstruction;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
* @author peter
*/
final class ContractChecker {
  private static class ContractCheckerVisitor extends StandardInstructionVisitor {
    private final PsiMethod myMethod;
    private final StandardMethodContract myContract;
    private final boolean myOwnContract;
    private final Set<PsiElement> myViolations = new HashSet<>();
    private final Set<PsiElement> myNonViolations = new HashSet<>();
    private final Set<PsiElement> myFailures = new HashSet<>();
    private boolean myMayReturnNormally = false;

    ContractCheckerVisitor(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
      super(true);
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
      PsiCall call = instruction.getCallExpression();
      if (!memState.isEphemeral() && call != null) {
        if (myContract.getReturnValue().isFail()) {
          myFailures.add(call);
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

    @Override
    public DfaInstructionState @NotNull [] visitControlTransfer(@NotNull ControlTransferInstruction instruction,
                                                                @NotNull DataFlowRunner runner,
                                                                @NotNull DfaMemoryState state) {
      if (instruction instanceof ReturnInstruction && ((ReturnInstruction)instruction).isViaException()) {
        ContainerUtil.addIfNotNull(myFailures, ((ReturnInstruction)instruction).getAnchor());
      }
      else {
        myMayReturnNormally = true;
      }
      return super.visitControlTransfer(instruction, runner, state);
    }

    private Map<PsiElement, @InspectionMessage String> getErrors() {
      HashMap<PsiElement, @InspectionMessage String> errors = new HashMap<>();
      for (PsiElement element : myViolations) {
        if (!myNonViolations.contains(element)) {
          errors.put(element, JavaAnalysisBundle.message("inspection.contract.checker.contract.violated", myContract));
        }
      }

      if (!myContract.getReturnValue().isFail()) {
        if (myOwnContract && !myMayReturnNormally &&
            !(PsiUtil.canBeOverridden(myMethod) && ControlFlowUtils.methodAlwaysThrowsException(myMethod))) {
          for (PsiElement element : myFailures) {
            if (myContract.isTrivial()) {
              errors.put(element, JavaAnalysisBundle.message("inspection.contract.checker.method.always.fails.trivial", myContract));
            }
            else {
              errors.put(element, JavaAnalysisBundle.message("inspection.contract.checker.method.always.fails.nontrivial", myContract));
            }
          }
        }
      } else if (myFailures.isEmpty() && errors.isEmpty() && myMayReturnNormally) {
        PsiIdentifier nameIdentifier = myMethod.getNameIdentifier();
        errors.put(nameIdentifier != null ? nameIdentifier : myMethod,
                   JavaAnalysisBundle.message("inspection.contract.checker.no.exception.thrown", myContract));
      }

      return errors;
    }

    private static boolean weCannotInferAnythingAboutMethodReturnValue(MethodCallInstruction instruction) {
      PsiMethod target = instruction.getTargetMethod();
      return instruction.getContracts().isEmpty() && target != null && !target.isConstructor() && !NullableNotNullManager.isNotNull(target);
    }
  }

  static Map<PsiElement, @InspectionMessage String> checkContractClause(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
    PsiCodeBlock body = method.getBody();
    if (body == null) return Collections.emptyMap();

    DataFlowRunner runner = new DataFlowRunner(method.getProject(), null);

    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = runner.createMemoryState();
    final DfaValueFactory factory = runner.getFactory();
    for (int i = 0; i < contract.getParameterCount(); i++) {
      ValueConstraint constraint = contract.getParameterConstraint(i);
      DfaValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = factory.getVarFactory().createVariableValue(parameters[i]);
        initialState.applyCondition(dfaParam.cond(RelationType.equivalence(!negated), comparisonValue));
      }
    }

    ContractCheckerVisitor visitor = new ContractCheckerVisitor(method, contract, ownContract);
    runner.analyzeMethod(body, visitor, Collections.singletonList(initialState));
    return visitor.getErrors();
  }
}
