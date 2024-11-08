// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Assign the value from the stack to a specified static destination
 */
public class SimpleAssignmentInstruction extends ExpressionPushingInstruction {
  private final @NotNull DfaVariableValue myDestination;

  public SimpleAssignmentInstruction(@Nullable DfaAnchor anchor, @NotNull DfaVariableValue destination) {
    super(anchor);
    myDestination = destination;
  }

  public @NotNull DfaVariableValue getDestination() {
    return myDestination;
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new SimpleAssignmentInstruction(getDfaAnchor(), myDestination.bindToFactory(factory));
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter,
                                      @NotNull DfaMemoryState stateBefore) {
    DfaValue value = stateBefore.pop();
    interpreter.getListener().beforeAssignment(value, myDestination, stateBefore, getDfaAnchor());
    stateBefore.setVarValue(myDestination, value);
    pushResult(interpreter, stateBefore, myDestination);
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "ASSIGN_TO " + myDestination;
  }
}
