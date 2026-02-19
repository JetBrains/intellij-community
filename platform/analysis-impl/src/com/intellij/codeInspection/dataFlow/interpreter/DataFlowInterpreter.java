// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.interpreter;

import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An interpreter that performs abstract interpretation of {@link com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow}
 */
public interface DataFlowInterpreter {
  /**
   * @return factory associated with this runner, can be used to create new values when necessary
   */
  @NotNull DfaValueFactory getFactory();

  /**
   * Perform an abstract interpretation with a specified starting memory state (at instruction index #0)
   * 
   * @param startingState starting memory state (at instruction index #0)
   * @return result of interpretation
   */
  @NotNull RunnerResult interpret(@NotNull DfaMemoryState startingState);

  /**
   * Perform an abstract interpretation with a list of initial states
   *
   * @param startingStates initial states
   * @return result of interpretation
   */
  @NotNull RunnerResult interpret(@NotNull List<? extends DfaInstructionState> startingStates);

  /**
   * Call this method from the visitor to cancel analysis (e.g. if wanted fact is already established and subsequent analysis
   * is useless). In this case {@link RunnerResult#CANCELLED} will be returned.
   */
  void cancel();

  /**
   * @return a complexity limit number that allows to trade-off between analysis quality and CPU time used.
   * If bigger number is returned, more complex methods could be analyzed, analysis quality is better but may require more CPU time.
   */
  int getComplexityLimit();

  /**
   * Creates and records a closure state from given memory state. It could be analyzed later in a separate pass.
   * 
   * @param anchor anchor to use
   * @param state initial closure state
   */
  void createClosureState(PsiElement anchor, DfaMemoryState state);

  /**
   * @param index instruction index
   * @return instruction of currently analyzed {@link com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow} at given index
   */
  @NotNull Instruction getInstruction(int index);

  /**
   * @return DfaInterceptor associated with current analysis
   */
  @NotNull DfaListener getListener(); 
}
