// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.jvm.SpecialField;
import com.intellij.codeInspection.dataFlow.jvm.descriptors.ArrayElementDescriptor;
import com.intellij.codeInspection.dataFlow.jvm.problems.IndexOutOfBoundsProblem;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.rangeSet.LongRangeSet;
import com.intellij.codeInspection.dataFlow.types.DfIntType;
import com.intellij.codeInspection.dataFlow.value.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Store array element. Pops (array, index, value) from the stack; pushes stored value
 */
public class ArrayStoreInstruction extends ExpressionPushingInstruction {
  protected final @Nullable DfaControlTransferValue myOutOfBoundsTransfer;
  protected final @NotNull IndexOutOfBoundsProblem myIndexProblem;
  protected final @Nullable VariableDescriptor myStaticVariable;

  public ArrayStoreInstruction(@Nullable DfaAnchor anchor,
                               @NotNull IndexOutOfBoundsProblem problem,
                               @Nullable DfaControlTransferValue outOfBoundsTransfer,
                               @Nullable VariableDescriptor variable) {
    super(anchor);
    myIndexProblem = problem;
    myOutOfBoundsTransfer = outOfBoundsTransfer;
    myStaticVariable = variable;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    DfaControlTransferValue transfer = myOutOfBoundsTransfer == null ? null : myOutOfBoundsTransfer.bindToFactory(factory);
    return new ArrayStoreInstruction(getDfaAnchor(), myIndexProblem, transfer, myStaticVariable);
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    DfaValue valueToStore = stateBefore.pop();
    DfaValue index = stateBefore.pop();
    DfaValue array = stateBefore.pop();
    List<DfaInstructionState> finalStates = new ArrayList<>();
    if (myOutOfBoundsTransfer != null) {
      finalStates.addAll(IndexOutOfBoundsProblem.dispatchTransfer(interpreter, stateBefore.createCopy(), myOutOfBoundsTransfer));
    }
    DfaInstructionState[] states =
      myIndexProblem.processOutOfBounds(interpreter, stateBefore, index, array, myOutOfBoundsTransfer);
    if (states != null) return states;

    JavaDfaHelpers.dropLocality(valueToStore, stateBefore);
    checkArrayElementAssignability(interpreter, stateBefore, valueToStore, array);

    LongRangeSet rangeSet = DfIntType.extractRange(stateBefore.getDfType(index));
    DfaValue arrayElementValue = ArrayElementDescriptor.getArrayElementValue(interpreter.getFactory(), array, rangeSet);
    interpreter.getListener().beforeAssignment(valueToStore, arrayElementValue, stateBefore, getDfaAnchor());
    if (arrayElementValue instanceof DfaVariableValue) {
      stateBefore.setVarValue((DfaVariableValue)arrayElementValue, valueToStore);
      pushResult(interpreter, stateBefore, arrayElementValue);
    }
    else {
      stateBefore.flushFieldsQualifiedBy(Set.of(array));
      pushResult(interpreter, stateBefore, valueToStore);
    }
    finalStates.add(nextState(interpreter, stateBefore));
    return finalStates.toArray(DfaInstructionState.EMPTY_ARRAY);
  }

  protected void checkArrayElementAssignability(@NotNull DataFlowInterpreter interpreter,
                                                @NotNull DfaMemoryState memState,
                                                @NotNull DfaValue dfaSource,
                                                @NotNull DfaValue qualifier) {
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return List.of(SpecialField.ARRAY_LENGTH);
  }

  @Override
  public String toString() {
    return "ARRAY_STORE " + getDfaAnchor();
  }
}
