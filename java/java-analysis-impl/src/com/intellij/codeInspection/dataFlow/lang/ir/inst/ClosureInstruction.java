/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
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
  public DfaInstructionState[] accept(@NotNull DataFlowRunner runner, @NotNull DfaMemoryState stateBefore) {
    for (PsiElement element : getClosureElements()) {
      runner.createClosureState(element, stateBefore);
    }
    return nextStates(runner, stateBefore);
  }

  public String toString() {
    return "CLOSURE";
  }
}
