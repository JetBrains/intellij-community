// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.VariableDescriptor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class FinishElementInstruction extends Instruction {
  private final Set<VariableDescriptor> myVarsToFlush = new HashSet<>();
  private final PsiElement myElement;

  public FinishElementInstruction(PsiElement element) {
    myElement = element;
  }

  @Override
  public DfaInstructionState[] accept(@NotNull DataFlowInterpreter interpreter, @NotNull DfaMemoryState state) {
    if (!myVarsToFlush.isEmpty()) {
      state.forgetVariables(var -> myVarsToFlush.contains(var.getDescriptor()));
    }
    return nextStates(interpreter, state);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    if (myVarsToFlush.isEmpty()) return this;
    // Derived analysis may change myVarsToFlush
    // E.g. see com.intellij.codeInspection.dataFlow.DfaPsiUtil#getBlockNotNullFields
    // (bad idea, but this is how it's done now)
    // So we still need to copy instruction to detach myVarsToFlush list
    var instruction = new FinishElementInstruction(myElement);
    instruction.flushVars(myVarsToFlush);
    return instruction;
  }

  /**
   * Add variables with given descriptors to the flush list
   *
   * @param vars vars to flush
   */
  public void flushVars(@NotNull Collection<@NotNull VariableDescriptor> vars) {
    myVarsToFlush.addAll(vars);
  }

  /**
   * Removes variable descriptors from the flush list that match the given predicate.
   *
   * @param predicate the predicate used to determine which variable descriptors to remove
   */
  public void removeFromFlushList(@NotNull Predicate<? super VariableDescriptor> predicate) {
    myVarsToFlush.removeIf(predicate);
  }

  /**
   * @return true if this instruction may flush some variables
   */
  public boolean mayFlushSomething() {
    return !myVarsToFlush.isEmpty();
  }

  @Override
  public String toString() {
    return "FINISH " + (myElement == null ? "" : myElement) + (myVarsToFlush.isEmpty() ? "" : "; flushing " + myVarsToFlush);
  }
}
