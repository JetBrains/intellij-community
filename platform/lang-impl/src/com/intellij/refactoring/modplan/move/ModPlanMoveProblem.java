// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * A reason why the subjects cannot go into the target directory, see {@link ModPlanMoveRefactoring#checkPlacement}.
 * The constants are in the order of the checks, except {@link #INVALID_SUBJECT}.
 * <p>
 * The engine checks the subjects, then {@link #INVALID_DESTINATION}, then the placement of each {@link ModPlanMoveProvider}. A provider
 * can give any problem. The last checks are the merged item moves of all owners.
 */
public enum ModPlanMoveProblem {
  /** A subject is not valid, no provider supports it, it is outside the project content, or it is a content root. */
  INVALID_SUBJECT,

  /** The target directory is outside the project content, or a part of its path is a file. */
  INVALID_DESTINATION,

  /** The target directory is a moved directory or is inside one. */
  MOVE_INTO_ITSELF,

  /** An item move keeps the item in its directory under the same name. */
  ALREADY_IN_DESTINATION,

  /** The target directory of an item move has a child with its new name, or two item moves have the same future path. */
  TARGET_EXISTS;

  /** @return the text that tells the user the problem */
  public @Nls @NotNull String message() {
    return switch (this) {
      case INVALID_SUBJECT -> RefactoringBundle.message("move.modplan.problem.invalid.subject");
      case INVALID_DESTINATION -> RefactoringBundle.message("move.modplan.problem.invalid.destination");
      case MOVE_INTO_ITSELF -> RefactoringBundle.message("move.modplan.problem.move.into.itself");
      case ALREADY_IN_DESTINATION -> RefactoringBundle.message("move.modplan.problem.already.in.destination");
      case TARGET_EXISTS -> RefactoringBundle.message("move.modplan.problem.target.exists");
    };
  }
}