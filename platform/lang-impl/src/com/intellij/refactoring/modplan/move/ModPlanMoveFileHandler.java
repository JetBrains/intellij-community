// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.psi.PsiFile;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFileHandler;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * The language side of the move of one file on the ModPlan engine: the adapter of a {@link MoveFileHandler}, see
 * {@link MoveFileHandler#asModPlan}. It gives the edits and the conflicts of the language, such as the package statement of the moved
 * file and the references to its classes. It writes no PSI and shows no UI.
 * <p>
 * The edits run on the copies before the files move. So the adapter computes every new path and name on the original files, from
 * {@link ModPlanMovePlaces}.
 */
public interface ModPlanMoveFileHandler {
  /** The adapter of a handler that adds nothing, such as the default handler. */
  ModPlanMoveFileHandler NOTHING = (file, places, request) -> new ModPlanAnalysisResult.Found(List.of(), ModPlanConflicts.EMPTY);

  /**
   * @param file    a moved file, or a file inside a moved directory, which the handler of this adapter processes
   * @param request the request of the move; read the options of {@link ModPlanMoveOptions} from it
   * @return the edits and the conflicts of the language for {@code file}
   */
  @RequiresReadLock
  @NotNull ModPlanAnalysisResult analyze(@NotNull PsiFile file, @NotNull ModPlanMovePlaces places, @NotNull ModPlanRequest request);
}