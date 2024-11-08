// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.java.JavaDfaHelpers;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Marks given variables as escaped (usually necessary for captured variables in lambdas/local classes)
 */
public class EscapeInstruction extends Instruction {
  private final @NotNull List<@NotNull VariableDescriptor> myEscapedDescriptors;

  public EscapeInstruction(@NotNull List<@NotNull VariableDescriptor> descriptors) {
    myEscapedDescriptors = descriptors;
  }

  @Override
  public List<VariableDescriptor> getRequiredDescriptors(@NotNull DfaValueFactory factory) {
    return myEscapedDescriptors;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    List<DfaVariableValue> escapedVars = StreamEx.of(interpreter.getFactory().getValues())
      .select(DfaVariableValue.class)
      .filter(var -> myEscapedDescriptors.contains(var.getDescriptor()))
      .toList();
    for (DfaVariableValue var : escapedVars) {
      JavaDfaHelpers.dropLocality(var, stateBefore);
    }
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "ESCAPE " + myEscapedDescriptors;
  }
}
