// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ArrayAccessInstruction extends ExpressionPushingInstruction {
  private final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;
  private final @NotNull IndexOutOfBoundsProblem myProblem;
  private final @Nullable DfaVariableValue myStaticValue;

  public ArrayAccessInstruction(@Nullable DfaAnchor anchor,
                                @NotNull IndexOutOfBoundsProblem indexProblem,
                                @Nullable DfaControlTransferValue outOfBoundsTransfer,
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
    return new ArrayAccessInstruction(getDfaAnchor(), myProblem, newTransfer, newStaticValue);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue index = stateBefore.pop();
    DfaValue array = stateBefore.pop();
    DfaInstructionState[] states = myProblem.processOutOfBounds(interpreter, stateBefore, index, array, myOutOfBoundsTransfer);
    if (states != null) return states;
    LongRangeSet rangeSet = DfIntType.extractRange(stateBefore.getDfType(index));
    DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(interpreter.getFactory(), array, rangeSet);
    DfaValue result;
    if (!DfaTypeValue.isUnknown(arrayElementValue)) {
      result = arrayElementValue;
    }
    else {
      DfType type = TypeConstraint.fromDfType(stateBefore.getDfType(array)).getArrayComponentType();
      type = type.meet(ArrayElementDescriptor.getArrayComponentType(array));
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

  @Override
  public List<DfaVariableValue> getRequiredVariables(DfaValueFactory factory) {
    return ContainerUtil.createMaybeSingletonList(myStaticValue);
  }

  @Override
  public String toString() {
    return "ARRAY_ACCESS " + getDfaAnchor();
  }
}
