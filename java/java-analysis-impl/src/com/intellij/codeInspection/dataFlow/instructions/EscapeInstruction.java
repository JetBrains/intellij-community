// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;

import java.util.Set;

/**
 * Marks given variables as escaped (usually necessary for captured variables in lambdas/local classes)
 */
public class EscapeInstruction extends Instruction {
  private final Set<DfaVariableValue> myEscapedVars;

  public EscapeInstruction(Set<DfaVariableValue> escapedVars) {myEscapedVars = escapedVars;}

  public Set<DfaVariableValue> getEscapedVars() {
    return myEscapedVars;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState stateBefore, InstructionVisitor visitor) {
    return visitor.visitEscapeInstruction(this, runner, stateBefore);
  }

  @Override
  public String toString() {
    return "ESCAPE " + myEscapedVars;
  }
}
