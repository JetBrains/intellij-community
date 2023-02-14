// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FinishElementInstruction extends Instruction {
  private final Set<DfaVariableValue> myVarsToFlush = new HashSet<>();
  private final PsiElement myElement;

  public FinishElementInstruction(PsiElement element) {
    myElement = element;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState state) {
    if (!myVarsToFlush.isEmpty()) {
      for (DfaVariableValue value : myVarsToFlush) {
        state.flushVariable(value, false);
      }
    }
    return nextStates(interpreter, state);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myVarsToFlush.isEmpty()) return this;
    var instruction = new FinishElementInstruction(myElement);
    for (DfaVariableValue var : myVarsToFlush) {
      instruction.myVarsToFlush.add(var.bindToFactory(factory));
    }
    return instruction;
  }

  @Override
  public List<DfaVariableValue> getWrittenVariables(DfaValueFactory factory) {
    return List.copyOf(myVarsToFlush);
  }

  @Override
  public String toString() {
    return "FINISH " + (myElement == null ? "" : myElement) + (myVarsToFlush.isEmpty() ? "" : "; flushing " + myVarsToFlush);
  }

  public Set<DfaVariableValue> getVarsToFlush() {
    return myVarsToFlush;
  }
}
