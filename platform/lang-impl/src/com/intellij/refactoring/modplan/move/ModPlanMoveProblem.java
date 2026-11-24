// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/** A reason why the moved items cannot go into the target directory, see {@link ModPlanMoveRefactoring#checkPlacement}. */
public enum ModPlanMoveProblem {
  /** An item is not valid, is outside the project content, or is a content root. */
  INVALID_SUBJECT,

  /** The target directory is outside the project content, or a part of its path is a file. */
  INVALID_DESTINATION,

  /** The target directory is a moved directory or is inside one. */
  MOVE_INTO_ITSELF,

  /** An item is already in the target directory. */
  ALREADY_IN_DESTINATION,

  /** The target directory has a child with the name of an item, or two items have the same name. */
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