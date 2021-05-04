/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.java.anchor.JavaExpressionAnchor;
import com.intellij.codeInspection.dataFlow.jvm.ControlTransferHandler;
import com.intellij.codeInspection.dataFlow.jvm.JvmSpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.ArrayIndexProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.intValue;

public class ArrayAccessInstruction extends ExpressionPushingInstruction {
  private final @NotNull DfaValue myValue;
  private final @Nullable PsiArrayAccessExpression myExpression;
  private final @Nullable DfaControlTransferValue myTransferValue;

  public ArrayAccessInstruction(@NotNull DfaValue value,
                                @Nullable PsiArrayAccessExpression expression,
                                @Nullable DfaControlTransferValue transferValue) {
    this(value, expression == null || PsiUtil.isAccessedForWriting(expression) && !PsiUtil.isAccessedForReading(expression) ? null : 
                new JavaExpressionAnchor(expression), transferValue, expression);
  }

  private ArrayAccessInstruction(@NotNull DfaValue value,
                                 @Nullable DfaAnchor anchor,
                                 @Nullable DfaControlTransferValue transferValue,
                                 @Nullable PsiArrayAccessExpression expression) {
    super(anchor);
    myValue = value;
    myTransferValue = transferValue;
    myExpression = expression;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    DfaControlTransferValue newTransfer = myTransferValue == null ? null : myTransferValue.bindToFactory(factory);
    var instruction = new ArrayAccessInstruction(myValue.bindToFactory(factory), getDfaAnchor(), newTransfer, myExpression);
    instruction.setIndex(getIndex());
    return instruction;
  }

  @NotNull
  public DfaValue getValue() {
    return myValue;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue index = stateBefore.pop();
    DfaValue array = stateBefore.pop();
    boolean alwaysOutOfBounds = !applyBoundsCheck(stateBefore, array, index);
    if (myExpression != null) {
      ThreeState failed = alwaysOutOfBounds ? ThreeState.YES : ThreeState.UNSURE;
      interpreter.getListener().onCondition(new ArrayIndexProblem(myExpression), index, failed, stateBefore);
    }
    if (alwaysOutOfBounds) {
      if (myTransferValue != null) {
        List<DfaInstructionState> states = ControlTransferHandler.dispatch(stateBefore, interpreter, myTransferValue);
        for (DfaInstructionState state : states) {
          state.getMemoryState().markEphemeral();
        }
        return states.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
      return DfaInstructionState.EMPTY_ARRAY;
    }

    DfaValue result = myValue;
    LongRangeSet rangeSet = DfIntType.extractRange(stateBefore.getDfType(index));
    DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(interpreter.getFactory(), array, rangeSet);
    if (!DfaTypeValue.isUnknown(arrayElementValue)) {
      result = arrayElementValue;
    }
    if (!(result instanceof DfaVariableValue) && array instanceof DfaVariableValue) {
      for (DfaVariableValue value : ((DfaVariableValue)array).getDependentVariables().toArray(new DfaVariableValue[0])) {
        if (value.getQualifier() == array) {
          JavaDfaHelpers.dropLocality(value, stateBefore);
        }
      }
    }
    pushResult(interpreter, stateBefore, result);
    return nextStates(interpreter, stateBefore);
  }

  private static boolean applyBoundsCheck(@NotNull DfaMemoryState memState,
                                          @NotNull DfaValue array,
                                          @NotNull DfaValue index) {
    DfaValueFactory factory = index.getFactory();
    DfaValue length = JvmSpecialField.ARRAY_LENGTH.createValue(factory, array);
    DfaCondition lengthMoreThanZero = length.cond(RelationType.GT, intValue(0));
    if (!memState.applyCondition(lengthMoreThanZero)) return false;
    DfaCondition indexNonNegative = index.cond(RelationType.GE, intValue(0));
    if (!memState.applyCondition(indexNonNegative)) return false;
    DfaCondition indexLessThanLength = index.cond(RelationType.LT, length);
    if (!memState.applyCondition(indexLessThanLength)) return false;
    return true;
  }

  @Override
  public String toString() {
    return "ARRAY_ACCESS " + getDfaAnchor();
  }
}
