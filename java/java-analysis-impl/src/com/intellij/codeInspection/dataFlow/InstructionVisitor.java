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
import com.intellij.codeInspection.dataFlow.lang.DfaInterceptor;
import com.intellij.codeInspection.dataFlow.lang.DfaLanguageSupport;
import com.intellij.codeInspection.dataFlow.value.DfaCondition;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

/**
 * @author peter
 */
public abstract class InstructionVisitor<EXPR extends PsiElement> {
  protected final @NotNull DfaLanguageSupport<EXPR> myLanguageSupport;
  protected final @NotNull DfaInterceptor<EXPR> myInterceptor;

  protected InstructionVisitor(@NotNull DfaLanguageSupport<EXPR> support, @Nullable DfaInterceptor<EXPR> interceptor) {
    myLanguageSupport = support;
    //noinspection unchecked
    myInterceptor = interceptor != null ? interceptor :
                    this instanceof DfaInterceptor ? (DfaInterceptor<EXPR>)this :
                    new DfaInterceptor<>() {};
  }

  void pushExpressionResult(@NotNull DfaValue value,
                            @NotNull ExpressionPushingInstruction<?> instruction,
                            @NotNull DfaMemoryState state) {
    myLanguageSupport.processExpressionPush(myInterceptor, value, instruction, state);
    state.push(value);
  }

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
        for (DfaVariableValue qualified : ((DfaVariableValue)value).getDependentVariables().toArray(new DfaVariableValue[0])) {
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

  public DfaInstructionState @NotNull [] visitControlTransfer(@NotNull ControlTransferInstruction controlTransferInstruction,
                                                              @NotNull DataFlowRunner runner, @NotNull DfaMemoryState state) {
    return controlTransferInstruction.getTransfer().dispatch(state, runner).toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public DfaInstructionState[] visitEndOfInitializer(EndOfInitializerInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    myInterceptor.beforeInitializerEnd(instruction.isStatic(), state);
    return nextInstruction(instruction, runner, state);
  }

  public DfaInstructionState[] visitEscapeInstruction(EscapeInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    return nextInstruction(instruction, runner, state);
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
    pushExpressionResult(runner.getFactory().getUnknown(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitConditionalGoto(ConditionalGotoInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    DfaCondition condTrue = memState.pop().eq(runner.getFactory().getBoolean(!instruction.isNegated()));
    DfaCondition condFalse = condTrue.negate();

    PsiElement anchor = instruction.getPsiAnchor();
    if (condTrue == DfaCondition.getTrue()) {
      if (anchor != null) {
        myInterceptor.beforeConditionalJump(anchor, true);
      }
      return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(instruction.getOffset()), memState)};
    }

    if (condFalse == DfaCondition.getTrue()) {
      if (anchor != null) {
        myInterceptor.beforeConditionalJump(anchor, false);
      }
      return nextInstruction(instruction, runner, memState);
    }

    ArrayList<DfaInstructionState> result = new ArrayList<>(2);

    DfaMemoryState elseState = memState.createCopy();

    if (memState.applyCondition(condTrue)) {
      result.add(new DfaInstructionState(runner.getInstruction(instruction.getOffset()), memState));
      if (anchor != null) {
        myInterceptor.beforeConditionalJump(anchor, true);
      }
    }

    if (elseState.applyCondition(condFalse)) {
      result.add(new DfaInstructionState(runner.getInstruction(instruction.getIndex() + 1), elseState));
      if (anchor != null) {
        myInterceptor.beforeConditionalJump(anchor, false);
      }
    }

    return result.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public DfaInstructionState[] visitMethodReference(MethodReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    pushExpressionResult(runner.getFactory().getUnknown(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitFlushVariable(FlushVariableInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.flushVariable(instruction.getVariable());
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitFlushFields(FlushFieldsInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.flushFields();
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitMethodCall(MethodCallInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    for(int i = instruction.getArgCount(); i > 0; i--) {
      memState.pop();
    }

    memState.pop(); //qualifier
    pushExpressionResult(runner.getFactory().getUnknown(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    pushExpressionResult(runner.getFactory().getUnknown(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitArrayAccess(ArrayAccessInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop(); // index
    memState.pop(); // array reference
    pushExpressionResult(instruction.getValue(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitTypeCast(TypeCastInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    pushExpressionResult(memState.pop(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitClosureInstruction(ClosureInstruction instruction, DataFlowRunner runner, DfaMemoryState before) {
    return nextInstruction(instruction, runner, before);
  }

  public DfaInstructionState[] visitEval(EvalInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    int operands = instruction.getOperands();
    for (int i = 0; i < operands; i++) {
      memState.pop();
    }
    pushExpressionResult(runner.getFactory().getUnknown(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitArraySizeCheck(ArraySizeCheckInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    return nextInstruction(instruction, runner, memState);
  }
}
