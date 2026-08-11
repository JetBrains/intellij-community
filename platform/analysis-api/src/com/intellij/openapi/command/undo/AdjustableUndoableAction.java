// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An {@link UndoableAction} which can be reordered within an undo-stack.
 * It is a key prerequisite for the implementation of independent undo-stacks per user
 * so each user can undo his own changes without accidentally reverting foreign ones made in the same document.
 * In order to become adjustable, an {@link UndoableAction} must be able to <b>decompose</b> itself,
 * e.g., for each affected document it must provide a list of {@link ActionChangeRange}s
 * which effectively represent the changed document's fragments.
 * @deprecated CWM per-user undo stacks are being removed under IJPL-248573.
 */
@ApiStatus.Experimental
@Deprecated(forRemoval = true)
@SuppressWarnings("DeprecatedIsStillUsed")
public interface AdjustableUndoableAction extends UndoableAction {
  /**
   * @return a list of change ranges for a given document reference if the reference is actually
   * affected by this action and an empty list otherwise
   * @deprecated CWM per-user undo stacks are being removed under IJPL-248573.
   */
  @Deprecated(forRemoval = true)
  @NotNull List<MutableActionChangeRange> getChangeRanges(@NotNull DocumentReference reference);
}
