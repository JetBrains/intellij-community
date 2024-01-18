// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.interpreter;

import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.Set;

/**
 * An extended version of {@link StandardDataFlowInterpreter} which
 * tracks the instruction reachability.
 */
public class ReachabilityCountingInterpreter extends StandardDataFlowInterpreter {
  protected final @NotNull BitSet myReached = new BitSet();

  /**
   * @param flow control flow to interpret
   * @param listener listener to use
   * @param stopOnNull whether to stop interpretation on inevitable NullPointerException
   * @param startingIndex starting instruction index (usually, 0)
   */
  public ReachabilityCountingInterpreter(@NotNull ControlFlow flow, @NotNull DfaListener listener, boolean stopOnNull, int startingIndex) {
    super(flow, listener, stopOnNull);
    myReached.set(0, startingIndex);
  }

  @Override
  protected DfaInstructionState @NotNull [] acceptInstruction(@NotNull DfaInstructionState instructionState) {
    myReached.set(instructionState.getInstruction().getIndex());
    return super.acceptInstruction(instructionState);
  }

  public @NotNull Set<PsiElement> getUnreachable() {
    return myFlow.computeUnreachable(myReached);
  }
}
