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

import com.intellij.codeInspection.dataFlow.instructions.CheckReturnValueInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.instructions.MethodCallInstruction;
import com.intellij.codeInspection.dataFlow.instructions.ReturnInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
* @author peter
*/
class ContractChecker extends DataFlowRunner {
  private final PsiMethod myMethod;
  private final MethodContract myContract;
  private final boolean myOnTheFly;
  private final Set<PsiElement> myViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myNonViolations = ContainerUtil.newHashSet();
  private final Set<PsiElement> myFailures = ContainerUtil.newHashSet();

  private ContractChecker(PsiMethod method, MethodContract contract, final boolean onTheFly) {
    myMethod = method;
    myContract = contract;
    myOnTheFly = onTheFly;
  }

  static Map<PsiElement, String> checkContractClause(PsiMethod method,
                                                     MethodContract contract,
                                                     boolean ignoreAssertions, final boolean onTheFly) {

    PsiCodeBlock body = method.getBody();
    if (body == null) return Collections.emptyMap();

    ContractChecker checker = new ContractChecker(method, contract, onTheFly);

    PsiParameter[] parameters = method.getParameterList().getParameters();
    final DfaMemoryState initialState = checker.createMemoryState();
    final DfaValueFactory factory = checker.getFactory();
    for (int i = 0; i < contract.arguments.length; i++) {
      MethodContract.ValueConstraint constraint = contract.arguments[i];
      DfaConstValue comparisonValue = constraint.getComparisonValue(factory);
      if (comparisonValue != null) {
        boolean negated = constraint.shouldUseNonEqComparison();
        DfaVariableValue dfaParam = factory.getVarFactory().createVariableValue(parameters[i], false);
        initialState.applyCondition(factory.getRelationFactory().createRelation(dfaParam, comparisonValue, JavaTokenType.EQEQ, negated));
      }
    }

    checker.analyzeMethod(body, new StandardInstructionVisitor(), ignoreAssertions, Arrays.asList(initialState));
    return checker.getErrors();
  }

  @Override
  protected boolean shouldCheckTimeLimit() {
    if (!myOnTheFly) return false;
    return super.shouldCheckTimeLimit();
  }

  @NotNull
  @Override
  protected DfaInstructionState[] acceptInstruction(@NotNull InstructionVisitor visitor, @NotNull DfaInstructionState instructionState) {
    DfaMemoryState memState = instructionState.getMemoryState();
    if (memState.isEphemeral()) {
      return DfaInstructionState.EMPTY_ARRAY;
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
        ((MethodCallInstruction)instruction).getMethodType() == MethodCallInstruction.MethodType.REGULAR_METHOD_CALL &&
        myContract.returnValue == MethodContract.ValueConstraint.THROW_EXCEPTION) {
      ContainerUtil.addIfNotNull(myFailures, ((MethodCallInstruction)instruction).getCallExpression());
      return DfaInstructionState.EMPTY_ARRAY;
    }

    return super.acceptInstruction(visitor, instructionState);
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
