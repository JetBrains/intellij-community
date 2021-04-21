// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.value;

import com.intellij.psi.PsiElement;
import com.intellij.util.containers.FList;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

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

  // TransferTarget should be reusable in another factory
  public interface TransferTarget {
    /**
     * @return list of possible instruction offsets for given target
     */
    default @NotNull Collection<@NotNull Integer> getPossibleTargets() {
      return Collections.emptyList();
    }
  }

  public interface Trap {
    /**
     * @return list of possible instruction offsets for given trap
     */
    default @NotNull Collection<@NotNull Integer> getPossibleTargets() {
      return Collections.emptyList();
    }

    /**
     * @return PSI anchor (e.g. try section)
     */
    @NotNull PsiElement getAnchor();
  }
}
