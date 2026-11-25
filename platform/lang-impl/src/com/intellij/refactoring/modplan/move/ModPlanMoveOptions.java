// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import org.jetbrains.annotations.NotNull;

/** The move options that every language may read. */
public final class ModPlanMoveOptions {
  /**
   * The VFS URL of the target directory. The directory may not exist: the move creates it and its missing parents. A request without
   * it is refused.
   */
  public static final @NotNull ModPlanOption<String> TARGET_DIRECTORY =
    ModPlanOption.text("targetDirectory", "", RefactoringBundle.messagePointer("move.files.to.directory.label"));
  /**
   * Whether the move updates the references, as
   * {@link com.intellij.refactoring.RefactoringSettings#MOVE_SEARCH_FOR_REFERENCES_FOR_FILE} does in the legacy move. With the option
   * off, the move searches nothing. The references to the moved items and the file references in the moved files keep their text, and no
   * reference is a conflict. A language still edits the moved files, as {@link MoveFileHandler#updateMovedFile} does. It searches and
   * edits no usage: the legacy move calls {@link MoveFileHandler#findUsages} only with the option on.
   */
  public static final @NotNull ModPlanOption<Boolean> SEARCH_FOR_REFERENCES =
    ModPlanOption.bool("searchForReferences", true, RefactoringBundle.messagePointer("search.for.references"));
  /**
   * Whether a language also searches the comments and the strings, as {@link MoveFileHandler#findUsages} does with
   * {@code searchInComments}.
   */
  public static final @NotNull ModPlanOption<Boolean> SEARCH_IN_COMMENTS =
    ModPlanOption.bool("searchInComments", false, RefactoringBundle.messagePointer("search.in.comments.and.strings"));
  /**
   * Whether a language also searches the text occurrences in the files that are not code, as {@link MoveFileHandler#findUsages} does
   * with {@code searchInNonJavaFiles}.
   */
  public static final @NotNull ModPlanOption<Boolean> SEARCH_FOR_TEXT =
    ModPlanOption.bool("searchForText", false, RefactoringBundle.messagePointer("search.for.text.occurrences"));

  private ModPlanMoveOptions() {
  }
}