// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.intValue;

public class ArrayAccessInstruction extends ExpressionPushingInstruction {
  private final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;
  private final @NotNull UnsatisfiedConditionProblem myProblem;
  private final @Nullable DfaVariableValue myStaticValue;

  public ArrayAccessInstruction(@Nullable DfaControlTransferValue outOfBoundsTransfer,
                                @Nullable DfaAnchor anchor,
                                @NotNull UnsatisfiedConditionProblem indexProblem,
                                @Nullable DfaVariableValue staticValue) {
    super(anchor);
    myOutOfBoundsTransfer = outOfBoundsTransfer;
    myProblem = indexProblem;
    myStaticValue = staticValue;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    DfaControlTransferValue newTransfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    DfaVariableValue newStaticValue = myStaticValue == null ? null : myStaticValue.bindToFactory(factory);
    var instruction = new ArrayAccessInstruction(newTransfer, getDfaAnchor(), myProblem, newStaticValue);
    instruction.setIndex(getIndex());
    return instruction;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue index = stateBefore.pop();
    DfaValue array = stateBefore.pop();
    DfaInstructionState[] states = processOutOfBounds(myOutOfBoundsTransfer, interpreter, stateBefore, index, array, myProblem);
    if (states != null) return states;
    LongRangeSet rangeSet = DfIntType.extractRange(stateBefore.getDfType(index));
    DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(interpreter.getFactory(), array, rangeSet);
    DfaValue result;
    if (!DfaTypeValue.isUnknown(arrayElementValue)) {
      result = arrayElementValue;
    }
    else {
      DfType type = TypeConstraint.fromDfType(stateBefore.getDfType(array)).getArrayComponentType();
      result = type == DfType.BOTTOM ? interpreter.getFactory().getUnknown() : interpreter.getFactory().fromDfType(type);
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

  static DfaInstructionState @Nullable [] processOutOfBounds(@Nullable DfaControlTransferValue outOfBoundsTransfer,
                                                             @NotNull DataFlowInterpreter interpreter,
                                                             @NotNull DfaMemoryState stateBefore,
                                                             @NotNull DfaValue index,
                                                             @NotNull DfaValue array,
                                                             @NotNull UnsatisfiedConditionProblem indexProblem) {
    boolean alwaysOutOfBounds = !applyBoundsCheck(stateBefore, array, index);
    ThreeState failed = alwaysOutOfBounds ? ThreeState.YES : ThreeState.UNSURE;
    interpreter.getListener().onCondition(indexProblem, index, failed, stateBefore);
    if (alwaysOutOfBounds) {
      if (outOfBoundsTransfer != null) {
        List<DfaInstructionState> states = outOfBoundsTransfer.dispatch(stateBefore, interpreter);
        for (DfaInstructionState state : states) {
          state.getMemoryState().markEphemeral();
        }
        return states.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
      return DfaInstructionState.EMPTY_ARRAY;
    }
    return null;
  }

  private static boolean applyBoundsCheck(@NotNull DfaMemoryState memState,
                                          @NotNull DfaValue array,
                                          @NotNull DfaValue index) {
    DfaValueFactory factory = index.getFactory();
    DfaValue length = SpecialField.ARRAY_LENGTH.createValue(factory, array);
    DfaCondition lengthMoreThanZero = length.cond(RelationType.GT, intValue(0));
    if (!memState.applyCondition(lengthMoreThanZero)) return false;
    DfaCondition indexNonNegative = index.cond(RelationType.GE, intValue(0));
    if (!memState.applyCondition(indexNonNegative)) return false;
    DfaCondition indexLessThanLength = index.cond(RelationType.LT, length);
    if (!memState.applyCondition(indexLessThanLength)) return false;
    return true;
  }

  @Override
  public List<DfaVariableValue> getRequiredVariables(DfaValueFactory factory) {
    return ContainerUtil.createMaybeSingletonList(myStaticValue);
  }

  @Override
  public String toString() {
    return "ARRAY_ACCESS " + getDfaAnchor();
  }
}
