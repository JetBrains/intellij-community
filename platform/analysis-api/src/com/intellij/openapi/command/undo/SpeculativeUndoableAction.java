// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.undo;

import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;


@ApiStatus.Experimental
@ApiStatus.Internal
public interface SpeculativeUndoableAction {

  /* private */ Set<String> SPECULATIVE_UNDOABLE_COMMANDS = Set.of(
    "Typing",
    "Cut",
    "Cut Line Backward",
    "Cut up to Line End",
    "Paste",
    "Backspace",
    "Hungry Backspace",
    "Delete Line",
    "Delete to Line Start",
    "Delete to Line End",
    "Delete to Word Start",
    "Delete to Word End",
    "Move Line Up",
    "Move Line Down",
    "Move Statement Up",
    "Move Statement Down",
    "Move Caret to Line End",
    "Duplicate Line or Selection",
    "Comment with Line Comment",
    "Split Line",
    "Start New Line",
    "Start New Line Before Current",
    "Indent Selection"
  );

  static boolean isUndoable(
    @Nullable String commandName,
    boolean isGlobal,
    boolean isTransparent,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    @NotNull Collection<? extends UndoableAction> actions
  ) {
    if (commandName != null && !isGlobal && !isTransparent && undoConfirmationPolicy == UndoConfirmationPolicy.DEFAULT) {
      if (SPECULATIVE_UNDOABLE_COMMANDS.contains(commandName) && !actions.isEmpty()) {
        return ContainerUtil.and(
          actions,
          a -> a instanceof SpeculativeUndoableAction
        );
      }
    }
    return false;
  }
}
