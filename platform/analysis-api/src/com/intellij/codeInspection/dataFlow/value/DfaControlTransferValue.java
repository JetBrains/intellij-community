// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.psi.PsiElement;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A value that could be pushed to the stack and used for control transfer
 */
public final class DfaControlTransferValue extends DfaValue {
  final @NotNull TransferTarget target;
  final @NotNull FList<Trap> traps;

  DfaControlTransferValue(@NotNull DfaValueFactory factory,
                          @NotNull TransferTarget target,
                          @NotNull FList<Trap> traps) {
    super(factory);
    this.traps = traps;
    this.target = target;
  }

  public String toString() {
    return target + (traps.isEmpty() ? "" : " " + traps);
  }

  @Override
  public DfaControlTransferValue bindToFactory(DfaValueFactory factory) {
    return factory.controlTransfer(target, traps);
  }

  public @NotNull TransferTarget getTarget() {
    return target;
  }

  public @NotNull FList<Trap> getTraps() {
    return traps;
  }

  public int @NotNull [] getPossibleTargetIndices() {
    return IntStream.concat(traps
      .stream()
      .flatMap(trap -> trap.getPossibleTargets().stream())
      .mapToInt(x -> x),
      Arrays.stream(target.getPossibleTargets()))
      .distinct()
      .toArray();
  }

  public @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state, @NotNull DataFlowInterpreter interpreter) {
    return dispatch(state, interpreter, target, traps);
  }

  public static @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                            @NotNull DataFlowInterpreter interpreter,
                                                            @NotNull TransferTarget target,
                                                            @NotNull FList<Trap> nextTraps) {
    Trap head = nextTraps.getHead();
    nextTraps = nextTraps.getTail() == null ? FList.emptyList() : nextTraps.getTail();
    state.emptyStack();
    if (head != null) {
      return head.dispatch(state, interpreter, target, nextTraps);
    }
    return target.dispatch(state, interpreter);
  }


  /**
   * Represents the target location.
   * TransferTarget should be reusable in another factory
   */
  public interface TransferTarget {
    /**
     * @return list of possible instruction offsets for given target
     */
    default int @NotNull [] getPossibleTargets() {
      return ArrayUtil.EMPTY_INT_ARRAY;
    }

    /** 
     * @return next instruction states assuming no traps 
     */
    default @NotNull List<@NotNull DfaInstructionState> dispatch(@NotNull DfaMemoryState state, @NotNull DataFlowInterpreter interpreter) {
      return Collections.emptyList();
    }
  }

  /**
   * A transfer that returns from the current scope
   */
  public static final TransferTarget RETURN_TRANSFER = new TransferTarget() {
    @Override
    public String toString() {
      return "Return";
    }
  };

  /**
   * Represents traps (e.g. catch sections) that may prevent normal transfer
   */
  public interface Trap {
    /**
     * @return list of possible instruction offsets for given trap
     */
    default @NotNull Collection<@NotNull Integer> getPossibleTargets() {
      return Collections.emptyList();
    }

    default void link(DfaControlTransferValue value) {
    }

    @NotNull List<DfaInstructionState> dispatch(@NotNull DfaMemoryState state,
                                                @NotNull DataFlowInterpreter interpreter,
                                                @NotNull TransferTarget target,
                                                @NotNull FList<Trap> nextTraps);

    /**
     * @return PSI anchor (e.g. catch section)
     */
    @NotNull PsiElement getAnchor();
  }
}
