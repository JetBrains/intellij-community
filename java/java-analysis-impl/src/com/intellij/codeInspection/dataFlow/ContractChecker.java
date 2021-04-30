// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInspection.dataFlow.StandardMethodContract.ValueConstraint;
import com.intellij.codeInspection.dataFlow.java.JavaDfaInterceptor;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.PlainDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ContractFailureProblem;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.RelationType;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
* @author peter
*/
final class ContractChecker {
  private static class ContractCheckInterceptor implements JavaDfaInterceptor {
    private final PsiMethod myMethod;
    private final StandardMethodContract myContract;
    private final boolean myOwnContract;
    private final Set<PsiElement> myViolations = new HashSet<>();
    private final Set<PsiElement> myNonViolations = new HashSet<>();
    private final Set<PsiElement> myFailures = new HashSet<>();
    private boolean myMayReturnNormally = false;

    ContractCheckInterceptor(PsiMethod method, StandardMethodContract contract, boolean ownContract) {
      myMethod = method;
      myContract = contract;
      myOwnContract = ownContract;
    }

    @Override
    public void beforeValueReturn(@NotNull DfaValue value,
                                  @Nullable PsiExpression expression,
                                  @NotNull PsiElement context,
                                  @NotNull DfaMemoryState state) {
      if (context != myMethod || expression == null || state.isEphemeral()) return;
      if (!myContract.getReturnValue().isValueCompatible(state, value)) {
        myViolations.add(expression);
      } else {
        myNonViolations.add(expression);
      }
    }

    @Override
    public void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                            @NotNull DfaValue value,
                            @NotNull ThreeState failed,
                            @NotNull DfaMemoryState state) {
      if (problem instanceof ContractFailureProblem && failed != ThreeState.NO) {
        ContainerUtil.addIfNotNull(myFailures, ((ContractFailureProblem)problem).getAnchor());
      }
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

    ContractCheckInterceptor interceptor = new ContractCheckInterceptor(method, contract, ownContract);
    StandardDataFlowRunner runner = new StandardDataFlowRunner(method.getProject(), null) {
      @Override 
      public boolean stopOnNull() {
        return true;
      }

      @Override
      protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
        Instruction instruction = instructionState.getInstruction();
        DfaMemoryState memState = instructionState.getMemoryState();
        if (instruction instanceof ReturnInstruction) {
          if (((ReturnInstruction)instruction).isViaException()) {
            ContainerUtil.addIfNotNull(interceptor.myFailures, ((ReturnInstruction)instruction).getAnchor());
          }
          else {
            interceptor.myMayReturnNormally = true;
          }
        }
        else if (instruction instanceof MethodCallInstruction) {
          PsiCall call = ((MethodCallInstruction)instruction).getCallExpression();
          if (!memState.isEphemeral() && call != null) {
            if (interceptor.myContract.getReturnValue().isFail()) {
              interceptor.myFailures.add(call);
              return DfaInstructionState.EMPTY_ARRAY;
            }
            if (ContractCheckInterceptor.weCannotInferAnythingAboutMethodReturnValue((MethodCallInstruction)instruction)) {
              DfaInstructionState[] states = super.acceptInstruction(instructionState);
              for (DfaInstructionState state: states) {
                state.getMemoryState().markEphemeral();
              }
              return states;
            }
          }
        }
        return super.acceptInstruction(instructionState);
      }
    };

    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = runner.createMemoryState();
    final DfaValueFactory factory = runner.getFactory();
    for (int i = 0; i < contract.getParameterCount(); i++) {
      ValueConstraint constraint = contract.getParameterConstraint(i);
      DfaValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = PlainDescriptor.createVariableValue(factory, parameters[i]);
        initialState.applyCondition(dfaParam.cond(RelationType.equivalence(!negated), comparisonValue));
      }
    }

    runner.analyzeMethod(body, interceptor, Collections.singletonList(initialState));
    return interceptor.getErrors();
  }
}
