// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Indicates the closures inside the analyzed code block
 */
public class ClosureInstruction extends Instruction {
  private final @NotNull List<PsiElement> myClosures;

  public ClosureInstruction(@NotNull List<PsiElement> closures) {
    myClosures = closures;
  }

  public @NotNull List<PsiElement> getClosureElements() {
    return myClosures;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState stateBefore) {
    for (PsiElement element : getClosureElements()) {
      interpreter.createClosureState(element, stateBefore);
    }
    return nextStates(interpreter, stateBefore);
  }

  @Override
  public String toString() {
    return "CLOSURE";
  }
}
