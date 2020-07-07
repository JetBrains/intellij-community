// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.model.psi.PsiSymbolReference;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * An object representing a model branch: a way of creating non-physical copies of parts of PSI/document/VFS model,
 * performing changes on them in background and then inspecting and/or applying the changes back to the real model.
 */
@ApiStatus.Experimental
public interface ModelBranch {

  /**
   * Perform the given action in a context of a new model branch. The action may populate branch with model non-physical copies
   * and perform changes on those copies.<p></p>
   *
   * The {@code action} is executed on the current thread. The branch object should only be used from the same thread.
   *
   * @return the patch object representing all the accumulated changes in the branch.
   */
  static @NotNull ModelPatch performInBranch(@NotNull Project project, @NotNull Consumer<ModelBranch> action) {
    return BranchService.getInstance().performInBranch(project, action);
  }


  // ----------------- find copy in the branch

  /**
   * @return the non-physical copy of the given file in this branch, if it has been copied at all.
   */
  @Nullable VirtualFile findFileCopy(@NotNull VirtualFile file);

  /**
   * @return the non-physical copy of the given PSI element in this branch,
   * if it has been copied at all and the copy file hasn't been modified yet.
   */
  <T extends PsiElement> @Nullable T findPsiCopy(@NotNull T original);

  /**
   * @return the non-physical copy of the given PSI reference in this branch,
   * if it has been copied at all and the copy file hasn't been modified yet.
   */
  <T extends PsiSymbolReference> @Nullable T findReferenceCopy(@NotNull T original);



  // ----------------- find or create copy

  /**
   * Finds or creates a non-physical copy of the given PSI element in this branch.
   * This may only be called for PSI inside a file (not packages or directories), and its document should be committed.
   */
  <T extends PsiElement> @NotNull T obtainPsiCopy(@NotNull T original);

  /**
   * Finds or creates a non-physical copy of the given PSI reference in this branch.
   * This may only be called for references which occur in {@link PsiElement#getReferences()},
   * and the corresponding document should be committed.
   */
  <T extends PsiSymbolReference> @NotNull T obtainReferenceCopy(@NotNull T original);



  // ----------------- find originals by branched model

  /**
   * Finds a PSI element in the real model that's equivalent (same range, same class) to the given one in this branch.
   * Calling this method makes most sense right after the branch patch has been applied inside {@link #runAfterMerge} runnable,
   * before any further changes to the physical PSI/documents, but after ensuring they're committed.
   */
  <T extends PsiElement> @Nullable T findOriginalPsi(@NotNull T branched);

  /**
   * @return an original file for the given one in this branch.
   */
  @Nullable VirtualFile findOriginalFile(@NotNull VirtualFile branched);



  // ----------------- find branch by branched model

  /**
   * @return a branch that's the given PSI element was copied by, or null if there's no such branch.
   */
  static @Nullable ModelBranch getPsiBranch(@NotNull PsiElement element) {
    return getFileBranch(element.getContainingFile().getViewProvider().getVirtualFile());
  }

  /**
   * @return a branch that's the given file was copied by, or null if there's no such branch.
   */
  static @Nullable ModelBranch getFileBranch(@NotNull VirtualFile file) {
    return file instanceof BranchedVirtualFile ? ((BranchedVirtualFile)file).branch : null;
  }


  
  // ----------------- other

  /**
   * @return a number changed each time any non-physical PSI created by this branch is changed.
   */
  long getBranchedPsiModificationCount();


  /**
   * Add a custom action to be performed when the branch is merged back ({@link ModelPatch#applyBranchChanges()}.
   */
  void runAfterMerge(@NotNull Runnable action);
}
