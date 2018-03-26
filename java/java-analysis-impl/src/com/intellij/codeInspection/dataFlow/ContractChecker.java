// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInsight.NullableNotNullManager;
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
import com.intellij.util.containers.ContainerUtil;
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
  private final Set<PsiElement> myViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myNonViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myFailures = ContainerUtil.newHashSet();

  private ContractChecker(PsiMethod method, StandardMethodContract contract) {
    super(false, null);
    myMethod = method;
    myContract = contract;
  }

  static Map<PsiElement, String> checkContractClause(PsiMethod method, StandardMethodContract contract) {

    PsiCodeBlock body = method.getBody();
    if (body == null) return Collections.emptyMap();

    ContractChecker checker = new ContractChecker(method, contract);

    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = checker.createMemoryState();
    final DfaValueFactory factory = checker.getFactory();
    for (int i = 0; i < contract.arguments.length; i++) {
      MethodContract.ValueConstraint constraint = contract.arguments[i];
      DfaConstValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = factory.getVarFactory().createVariableValue(parameters[i], false);
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
      if (breaksContract(retValue, myContract.returnValue, memState)) {
        myViolations.add(anchor);
      } else {
        myNonViolations.add(anchor);
      }
      return InstructionVisitor.nextInstruction(instruction, this, memState);

    }

    if (instruction instanceof ReturnInstruction) {
      if (((ReturnInstruction)instruction).isViaException() && myContract.returnValue != MethodContract.ValueConstraint.NOT_NULL_VALUE) {
        ContainerUtil.addIfNotNull(myFailures, ((ReturnInstruction)instruction).getAnchor());
      }
    }

    if (instruction instanceof MethodCallInstruction &&
        ((MethodCallInstruction)instruction).getMethodType() == MethodCallInstruction.MethodType.REGULAR_METHOD_CALL) {
      if (myContract.returnValue == MethodContract.ValueConstraint.THROW_EXCEPTION) {
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

    if (myContract.returnValue != MethodContract.ValueConstraint.THROW_EXCEPTION) {
      for (PsiElement element : myFailures) {
        errors.put(element, "Contract clause '" + myContract + "' is violated: exception might be thrown instead of returning " + myContract.returnValue);
      }
    } else if (myFailures.isEmpty() && errors.isEmpty()) {
      PsiIdentifier nameIdentifier = myMethod.getNameIdentifier();
      errors.put(nameIdentifier != null ? nameIdentifier : myMethod,
                 "Contract clause '" + myContract + "' is violated: no exception is thrown");
    }

    return errors;
  }

  private boolean breaksContract(DfaValue retValue, MethodContract.ValueConstraint constraint, DfaMemoryState state) {
    switch (constraint) {
      case NULL_VALUE: return state.isNotNull(retValue);
      case NOT_NULL_VALUE: return state.isNull(retValue);
      case TRUE_VALUE: return isEquivalentTo(retValue, getFactory().getConstFactory().getFalse(), state);
      case FALSE_VALUE: return isEquivalentTo(retValue, getFactory().getConstFactory().getTrue(), state);
      case THROW_EXCEPTION: return true;
      default: return false;
    }
  }

  private static boolean isEquivalentTo(DfaValue val, DfaConstValue constValue, DfaMemoryState state) {
    return val == constValue || val instanceof DfaVariableValue && constValue == state.getConstantValue((DfaVariableValue)val);
  }

}
