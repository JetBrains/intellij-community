/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.DfaUnknownValue;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

/**
 * @author peter
 */
public abstract class InstructionVisitor {

  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    DfaValue dest = memState.pop();
    memState.push(dest);
    flushArrayOnUnknownAssignment(instruction, runner.getFactory(), dest, memState);
    return nextInstruction(instruction, runner, memState);
  }

  protected void flushArrayOnUnknownAssignment(AssignInstruction instruction,
                                               DfaValueFactory factory,
                                               DfaValue dest,
                                               DfaMemoryState memState) {
    if (dest instanceof DfaVariableValue) return;
    PsiArrayAccessExpression arrayAccess =
      ObjectUtils.tryCast(PsiUtil.skipParenthesizedExprDown(instruction.getLExpression()), PsiArrayAccessExpression.class);
    if (arrayAccess != null) {
      PsiExpression array = arrayAccess.getArrayExpression();
      DfaValue value = factory.createValue(array);
      if (value instanceof DfaVariableValue) {
        for (DfaVariableValue qualified : factory.getVarFactory().getAllQualifiedBy((DfaVariableValue)value)) {
          if (qualified.isFlushableByCalls()) {
            memState.flushVariable(qualified);
          }
        }
      }
    }
  }

  public DfaInstructionState[] visitCheckNotNull(CheckNotNullInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return nextInstruction(instruction, runner, memState);
  }

  @NotNull
  public DfaInstructionState[] visitControlTransfer(@NotNull ControlTransferInstruction controlTransferInstruction,
                                                    @NotNull DataFlowRunner runner, @NotNull DfaMemoryState state) {
    DfaControlTransferValue transferValue = controlTransferInstruction.getTransfer();
    if (transferValue == null) {
      transferValue = (DfaControlTransferValue)state.pop();
    }
    return new ControlTransferHandler(state, runner, transferValue.getTarget()).iteration(transferValue.getTraps())
      .toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  protected static DfaInstructionState[] nextInstruction(Instruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), memState)};
  }

  public DfaInstructionState[] visitInstanceof(InstanceofInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return visitBinop(instruction, runner, memState);
  }

  public DfaInstructionState[] visitBinop(BinopInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    memState.pop();
    memState.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitCheckReturnValue(CheckReturnValueInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitLambdaExpression(LambdaInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitConditionalGoto(ConditionalGotoInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue cond = memState.pop();

    DfaValue condTrue;
    DfaValue condFalse;

    if (instruction.isNegated()) {
      condFalse = cond;
      condTrue = cond.createNegated();
    } else {
      condTrue = cond;
      condFalse = cond.createNegated();
    }

    if (condTrue == runner.getFactory().getConstFactory().getTrue()) {
      markBranchReachable(instruction, true);
      return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(instruction.getOffset()), memState)};
    }

    if (condFalse == runner.getFactory().getConstFactory().getTrue()) {
      markBranchReachable(instruction, false);
      return nextInstruction(instruction, runner, memState);
    }

    ArrayList<DfaInstructionState> result = new ArrayList<>(2);

    DfaMemoryState elseState = memState.createCopy();

    if (memState.applyCondition(condTrue)) {
      result.add(new DfaInstructionState(runner.getInstruction(instruction.getOffset()), memState));
      markBranchReachable(instruction, true);
    }

    if (elseState.applyCondition(condFalse)) {
      result.add(new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), elseState));
      markBranchReachable(instruction, false);
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  private static void markBranchReachable(ConditionalGotoInstruction instruction, boolean isTrueBranch) {
    if (isTrueBranch ^ instruction.isNegated()) {
      instruction.setTrueReachable();
    }
    else {
      instruction.setFalseReachable();
    }
  }


  public DfaInstructionState[] visitEmptyStack(EmptyStackInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.emptyStack();
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitFieldReference(DereferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    final DfaVariableValue variable = instruction.getVariable();
    if (variable != null) {
      memState.flushVariable(variable);
    } else {
      memState.flushFields();
    }
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    for(int i = instruction.getArgCount(); i > 0; i--) {
      memState.pop();
    }

    memState.pop(); //qualifier
    memState.push(DfaUnknownValue.getInstance());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitCast(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return visitMethodCall(instruction, runner, memState);
  }

  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaValue dfaValue = memState.pop();

    dfaValue = dfaValue.createNegated();
    memState.push(dfaValue);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.push(instruction.getValue());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitArrayAccess(ArrayAccessInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop(); // index
    memState.pop(); // array reference
    memState.push(instruction.getValue());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitEmptyInstruction(EmptyInstruction instruction, DataFlowRunner runner, DfaMemoryState before) {
    return nextInstruction(instruction, runner, before);
  }
}
