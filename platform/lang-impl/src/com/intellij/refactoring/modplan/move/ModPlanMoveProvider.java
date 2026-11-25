// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.move;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

/**
 * The language side of the move on the ModPlan engine: it decides where its subjects go, and it gives the edits that the move needs. It
 * writes no PSI, shows no UI and opens no modal progress.
 * <p>
 * The owner of a subject is the first provider in the extension order for which {@link #isOwner} is true. A subject that no provider owns,
 * or whose owner is {@linkplain #isLegacyOnly legacy only} for it, is not supported. Only the owner places and analyzes a subject.
 * <p>
 * <b>The order of the calls.</b> The engine calls the providers in this order, in one run:
 * <ol>
 *   <li>{@link #isOwner} and {@link #isLegacyOnly} find the owner of each subject.</li>
 *   <li>{@link #place} of each owner, with the subjects that it owns. The engine checks the merged item moves of all owners.</li>
 *   <li>{@link #analyze} of each owner, with the {@link ModPlanMovePlaces} of the item moves of all owners.</li>
 * </ol>
 * <p>
 * An edit runs on the copy of one file before the files move, so the analysis computes every new path from {@link ModPlanMovePlaces}.
 * The edits of an analysis are the edits of the content, {@code Create} and {@code Delete}. The engine writes the moves and the creation of
 * the directories from the placements.
 */
public interface ModPlanMoveProvider {
  ExtensionPointName<ModPlanMoveProvider> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.modPlanMoveProvider");

  /** @return whether the provider owns {@code subject}; the answer is cheap and searches nothing */
  @RequiresReadLock
  boolean isOwner(@NotNull SymbolPointer subject);

  /** @return whether the move of {@code subject} is not supported, so that the caller runs the legacy move */
  @ApiStatus.Internal
  @RequiresReadLock
  default boolean isLegacyOnly(@NotNull SymbolPointer subject) {
    return false;
  }

  /**
   * Decides where the subjects go. It searches nothing. A subject that the provider cannot move gives a refusal.
   * <p>
   * The item of each item move is the file or the directory of a subject. So no item repeats another or is inside another moved item.
   *
   * @param subjects        the subjects of the provider, none inside another subject
   * @param targetDirectory the target directory of the request: an existing directory or a {@code FutureVirtualFile}
   * @return the item moves and the emptied directories; a refusal with the problem and its place
   */
  @RequiresReadLock
  @NotNull Placement place(@NotNull List<SymbolPointer> subjects, @NotNull VirtualFile targetDirectory, @NotNull ModPlanRequest request);

  /**
   * Gives the edits, the conflicts and the usages of the subjects. The edits are of the kinds {@code Update}, {@code UpdateRange},
   * {@code ReplaceText}, {@code Create} and {@code Delete}. An edit of the kind {@code Move} or {@code CreateDirectory} makes the engine
   * throw an {@link IllegalArgumentException}: the engine writes them from the placements.
   *
   * @param places the places of the item moves of all owners
   * @return the edits and the conflicts, or a refusal of the move
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull Analysis analyze(@NotNull List<SymbolPointer> subjects,
                            @NotNull VirtualFile targetDirectory,
                            @NotNull ModPlanMovePlaces places,
                            @NotNull ModPlanRequest request);

  /**
   * The move of a file or a directory to a directory under a new name.
   *
   * @param targetDirectory an existing directory or a {@code FutureVirtualFile}
   */
  record ItemMove(@NotNull VirtualFile item, @NotNull VirtualFile targetDirectory, @NotNull @NonNls String newName) {
  }

  /** The answer of {@link #place}. */
  sealed interface Placement {
    /**
     * The subjects can go to the places of {@code itemMoves}.
     *
     * @param emptiedDirectories the directories that the item moves empty: the engine deletes them after the moves
     */
    record Accepted(@NotNull @Unmodifiable List<ItemMove> itemMoves,
                    @NotNull @Unmodifiable List<VirtualFile> emptiedDirectories) implements Placement {
    }

    /**
     * The subjects cannot move.
     *
     * @param place the element that causes the refusal; null if there is none
     */
    record Refused(@NotNull ModPlanMoveProblem problem, @Nullable PsiElement place) implements Placement {
    }
  }

  /**
   * The answer of {@link #analyze}.
   *
   * @param result the edits and the conflicts, or a refusal
   * @param usages the places that a preview shows
   * @param place  the element that causes a refusal; null if there is none, so that the refusal is at the first subject of the provider
   */
  record Analysis(@NotNull ModPlanAnalysisResult result,
                  @NotNull @Unmodifiable List<SmartPsiFileRange> usages,
                  @Nullable PsiElement place) {
  }
}