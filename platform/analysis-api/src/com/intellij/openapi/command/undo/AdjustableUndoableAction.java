// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command.undo;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * An {@link UndoableAction} which can be reordered within an undo-stack.
 * It is a key prerequisite for the implementation of independent undo-stacks per user
 * so each user can undo his own changes without accidentally reverting foreign ones made in the same document.
 * In order to become adjustable, an {@link UndoableAction} must be able to <b>decompose</b> itself,
 * e.g., for each affected document it must provide a list of {@link ActionChangeRange}s
 * which effectively represent the changed document's fragments
 */
public interface AdjustableUndoableAction extends UndoableAction {
  /**
   * @return a list of change ranges for a given document reference if the reference is actually
   * affected by this action and an empty list otherwise
   */
  @NotNull List<ActionChangeRange> getChangeRanges(@NotNull DocumentReference reference);

  /**
   * Mark all the change ranges reachable via this action as invalid, so they can be eventually
   * removed from the shared undo-stack. Typically, it is invoked when the action itself
   * cannot be reachable via any undo-stack anymore
   */
  void invalidateChangeRanges();
}
