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

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInspection.dataFlow.instructions.*;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

/**
 * @author peter
 */
public abstract class InstructionVisitor {

  /**
   * Called before a PsiExpression result is being pushed to the memory state stack during symbolic interpretation.
   * The result of single expression can be pushed many times to the different memory states.
   *
   * @param value a value being pushed
   * @param expression a physical PsiExpression which evaluates to given value.
   * @param range if not-null, specifies a part of expression which corresponds to the value (like "a ^ b" range in "a ^ b ^ c" expression).
   * @param state a memory state where expression is about to be pushed
   */
  protected void beforeExpressionPush(@NotNull DfaValue value,
                                      @NotNull PsiExpression expression,
                                      @Nullable TextRange range,
                                      @NotNull DfaMemoryState state) {

  }

  /**
   * Called before a result of method reference execution is being pushed to the memory state stack during symbolic interpretation.
   * It's not guaranteed that this method is called for every reachable non-void method reference. However if it's called for some method
   * reference, then all possible values will be passed here.
   *
   * @param value result of method reference execution
   * @param methodRef a method reference
   * @param state a memory state
   */
  protected void beforeMethodReferenceResultPush(@NotNull DfaValue value,
                                                 @NotNull PsiMethodReferenceExpression methodRef,
                                                 @NotNull DfaMemoryState state) {

  }

  /**
   * Called for every expression which corresponds to the method or lambda result.
   *
   * @param value expression value
   * @param expression an expression which produces given value. For conditional return like {@code return cond ? ifTrue : ifFalse;}
   *                   this method will be called for {@code ifTrue} and {@code ifFalse} separately.
   * @param context a method or lambda which result is being returned
   * @param state a memory state
   */
  protected void checkReturnValue(@NotNull DfaValue value,
                                  @NotNull PsiExpression expression,
                                  @NotNull PsiParameterListOwner context,
                                  @NotNull DfaMemoryState state) {

  }

  void pushExpressionResult(@NotNull DfaValue value,
                            @NotNull ExpressionPushingInstruction instruction,
                            @NotNull DfaMemoryState state) {
    PsiExpression anchor = instruction.getExpression();
    if (isExpressionPush(instruction, anchor)) {
      if (anchor instanceof PsiMethodReferenceExpression && !(instruction instanceof MethodReferenceInstruction)) {
        beforeMethodReferenceResultPush(value, (PsiMethodReferenceExpression)anchor, state);
      }
      else {
        callBeforeExpressionPush(value, instruction, state, anchor);
      }
    }
    state.push(value);
  }

  private static boolean isExpressionPush(@NotNull ExpressionPushingInstruction instruction, PsiExpression anchor) {
    if (anchor == null) return false;
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiAssignmentExpression) {
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)parent;
      if (assignment.getOperationTokenType().equals(JavaTokenType.EQ) &&
          PsiTreeUtil.isAncestor(assignment.getLExpression(), anchor, false)) {
        return false;
      }
    }
    if (instruction instanceof PushInstruction) {
      return !((PushInstruction)instruction).isReferenceWrite();
    }
    return true;
  }

  private void callBeforeExpressionPush(@NotNull DfaValue value,
                                        @NotNull ExpressionPushingInstruction instruction,
                                        @NotNull DfaMemoryState state, PsiExpression anchor) {
    beforeExpressionPush(value, anchor, instruction.getExpressionRange(), state);
    PsiElement parent = PsiUtil.skipParenthesizedExprUp(anchor.getParent());
    if (parent instanceof PsiLambdaExpression) {
      checkReturnValue(value, Objects.requireNonNull(instruction.getExpression()), (PsiLambdaExpression)parent, state);
    }
    else if (parent instanceof PsiReturnStatement) {
      PsiParameterListOwner context = PsiTreeUtil.getParentOfType(parent, PsiMethod.class, PsiLambdaExpression.class);
      if (context != null) {
        checkReturnValue(value, Objects.requireNonNull(instruction.getExpression()), context, state);
      }
    }
    else if (anchor instanceof PsiArrayInitializerExpression && parent instanceof PsiNewExpression) {
      callBeforeExpressionPush(value, instruction, state, (PsiExpression)parent);
    }
    else if (parent instanceof PsiConditionalExpression &&
        !PsiTreeUtil.isAncestor(((PsiConditionalExpression)parent).getCondition(), anchor, false)) {
      callBeforeExpressionPush(value, instruction, state, (PsiConditionalExpression)parent);
    }
    else if (parent instanceof PsiPolyadicExpression) {
      PsiPolyadicExpression polyadic = (PsiPolyadicExpression)parent;
      if ((polyadic.getOperationTokenType().equals(JavaTokenType.ANDAND) || polyadic.getOperationTokenType().equals(JavaTokenType.OROR)) &&
          PsiTreeUtil.isAncestor(ArrayUtil.getLastElement(polyadic.getOperands()), anchor, false)) {
        callBeforeExpressionPush(value, instruction, state, polyadic);
      }
    }
  }

  public DfaInstructionState[] visitAssign(AssignInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    DfaValue dest = memState.pop();
    memState.push(dest);
    flushArrayOnUnknownAssignment(instruction, runner.getFactory(), dest, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitBox(BoxingInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    DfaValue value = state.pop();
    DfaValueFactory factory = runner.getFactory();
    if (value instanceof DfaBinOpValue) {
      value = factory.getFactValue(DfaFactType.RANGE, state.getValueFact(value, DfaFactType.RANGE));
    }
    DfaValue boxed = factory.getBoxedFactory().createBoxed(value, instruction.getTargetType());
    state.push(boxed == null ? factory.createTypeValue(instruction.getTargetType(), Nullability.NOT_NULL) : boxed);
    return nextInstruction(instruction, runner, state);
  }

  public DfaInstructionState[] visitUnwrapField(UnwrapSpecialFieldInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    DfaValue value = state.pop();
    DfaValue field = instruction.getSpecialField().createValue(runner.getFactory(), value);
    state.push(field);
    return nextInstruction(instruction, runner, state);
  }

  public DfaInstructionState[] visitConvertPrimitive(PrimitiveConversionInstruction instruction,
                                                     DataFlowRunner runner,
                                                     DfaMemoryState state) {
    state.pop();
    pushExpressionResult(DfaUnknownValue.getInstance(), instruction, state);
    return nextInstruction(instruction, runner, state);
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
        for (DfaVariableValue qualified : ((DfaVariableValue)value).getDependentVariables()) {
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
    return controlTransferInstruction.getTransfer().dispatch(state, runner).toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  public DfaInstructionState[] visitEndOfInitializer(EndOfInitializerInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    return nextInstruction(instruction, runner, state);
  }

  public DfaInstructionState[] visitEscapeInstruction(EscapeInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    return nextInstruction(instruction, runner, state);
  }

  public DfaInstructionState[] visitResultOf(ResultOfInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    pushExpressionResult(state.pop(), instruction, state);
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
    pushExpressionResult(DfaUnknownValue.getInstance(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitObjectOfInstruction(ObjectOfInstruction instruction, DataFlowRunner runner, DfaMemoryState state) {
    DfaValue value = state.pop();
    DfaConstValue constant = state.getConstantValue(value);
    PsiType type = constant == null ? null : ObjectUtils.tryCast(constant.getValue(), PsiType.class);
    state.push(runner.getFactory().createTypeValue(type, Nullability.NOT_NULL));
    return nextInstruction(instruction, runner, state);
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

  public DfaInstructionState[] visitMethodReference(MethodReferenceInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    pushExpressionResult(DfaUnknownValue.getInstance(), instruction, memState);
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
    pushExpressionResult(DfaUnknownValue.getInstance(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitNot(NotInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    memState.pop();
    pushExpressionResult(DfaUnknownValue.getInstance(), instruction, memState);
    return nextInstruction(instruction, runner, memState);
  }

  public DfaInstructionState[] visitPush(PushInstruction instruction, DataFlowRunner runner, DfaMemoryState memState) {
    pushExpressionResult(instruction.getValue(), instruction, memState);
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

  public DfaInstructionState[] visitEmptyInstruction(EmptyInstruction instruction, DataFlowRunner runner, DfaMemoryState before) {
    return nextInstruction(instruction, runner, before);
  }
}
