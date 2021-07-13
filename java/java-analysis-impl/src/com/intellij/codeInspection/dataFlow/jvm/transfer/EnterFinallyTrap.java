// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.transfer;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlowOffset;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaControlTransferValue;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiResourceList;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class EnterFinallyTrap implements DfaControlTransferValue.Trap {
  private final PsiElement myAnchor;
  private final @NotNull ControlFlowOffset myJumpOffset;
  private final @NotNull List<@NotNull DfaControlTransferValue> myBackLinks = new ArrayList<>();

  public EnterFinallyTrap(PsiElement anchor, @NotNull ControlFlowOffset offset) {
    myAnchor = anchor;
    myJumpOffset = offset; 
  }

  @NotNull List<@NotNull DfaControlTransferValue> backLinks() {
    return myBackLinks;
  }

  @Override
  public void link(DfaControlTransferValue instruction) {
    myBackLinks.add(instruction);
  }

  @Override
  public @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                     @NotNull DataFlowInterpreter interpreter,
                                                     DfaControlTransferValue.@NotNull TransferTarget target,
                                                     @NotNull FList<DfaControlTransferValue.Trap> nextTraps) {
    state.push(interpreter.getFactory().controlTransfer(target, nextTraps));
    return List.of(new DfaInstructionState(interpreter.getInstruction(myJumpOffset.getInstructionOffset()), state));
  }

  @Override
  public @NotNull Collection<@NotNull Integer> getPossibleTargets() {
    return List.of(myJumpOffset.getInstructionOffset());
  }

  @NotNull
  @Override
  public PsiElement getAnchor() {
    return myAnchor;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " -> " + myJumpOffset;
  }

  public int getJumpOffset() {
    return myJumpOffset.getInstructionOffset();
  }

  public static class TryFinally extends EnterFinallyTrap {
    public TryFinally(@NotNull PsiCodeBlock finallyBlock, @NotNull ControlFlowOffset offset) {
      super(finallyBlock, offset);
    }
  }

  public static class TwrFinally extends EnterFinallyTrap {
    public TwrFinally(@NotNull PsiResourceList resourceList, @NotNull ControlFlowOffset offset) {
      super(resourceList, offset);
    }

    @Override
    public @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                       @NotNull DataFlowInterpreter interpreter,
                                                       DfaControlTransferValue.@NotNull TransferTarget target,
                                                       @NotNull FList<DfaControlTransferValue.Trap> nextTraps) {
      if (target instanceof ExceptionTransfer) {
        return DfaControlTransferValue.dispatch(state, interpreter, target, nextTraps);
      }
      return super.dispatch(state, interpreter, target, nextTraps);
    }
  }
}
