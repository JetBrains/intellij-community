// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.SimpleAssignmentInstruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Assign the value from the stack to a specified static destination
 */
public class JvmAssignmentInstruction extends SimpleAssignmentInstruction {
  public JvmAssignmentInstruction(@Nullable DfaAnchor anchor, @NotNull DfaVariableValue destination) {
    super(anchor, destination);
  }
  
  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new JvmAssignmentInstruction(getDfaAnchor(), getDestination().bindToFactory(factory));
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter,
                                      @NotNull DfaMemoryState stateBefore) {
    DfaValue value = stateBefore.peek();
    if (value instanceof DfaVariableValue) {
      if (!DfaValueFactory.isTempVariable((DfaVariableValue)value)) {
        JavaDfaHelpers.dropLocality(value, stateBefore);
      }
    }
    return super.accept(interpreter, stateBefore);
  }
}
