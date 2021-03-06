// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.InstructionVisitor;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;

import java.util.HashSet;
import java.util.Set;

/**
 * @author peter
 */
public class FinishElementInstruction extends Instruction {
  private final Set<DfaVariableValue> myVarsToFlush = new HashSet<>();
  private final PsiElement myElement;

  public FinishElementInstruction(PsiElement element) {
    myElement = element;
  }

  @Override
  public DfaInstructionState[] accept(DataFlowRunner runner, DfaMemoryState state, InstructionVisitor visitor) {
    if (!myVarsToFlush.isEmpty()) {
      for (DfaVariableValue value : myVarsToFlush) {
        state.flushVariable(value);
      }
    }
    return nextInstruction(runner, state);
  }

  @Override
  public String toString() {
    return "FINISH " + (myElement == null ? "" : myElement) + (myVarsToFlush.isEmpty() ? "" : "; flushing " + myVarsToFlush);
  }

  public Set<DfaVariableValue> getVarsToFlush() {
    return myVarsToFlush;
  }
}
