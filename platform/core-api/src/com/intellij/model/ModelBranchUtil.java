// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Helper methods to work with {@link ModelBranch} API
 */
public class ModelBranchUtil {
  private ModelBranchUtil() {}

  /**
   * @param context context element that may belong to the branch
   * @param element element to get the copy in the same branch as context
   * @param <T> type of the element
   * @return a copy of element that belongs to the same branch as context. 
   * May return input element if it already belongs to the same branch or if context doesn't belong to any branch
   * @throws IllegalArgumentException if element and context already belong to different branches
   */
  public static <T extends PsiElement> @NotNull T obtainCopyFromTheSameBranch(@NotNull PsiElement context, @NotNull T element) {
    ModelBranch branch = ModelBranch.getPsiBranch(context);
    if (branch == null) return element;
    ModelBranch elementBranch = ModelBranch.getPsiBranch(element);
    if (elementBranch == branch) return element;
    if (elementBranch != null) {
      throw new IllegalArgumentException("Branch of supplied element differs from context branch");
    }
    return branch.obtainPsiCopy(element);
  }

  /**
   * @param context context file that may belong to the branch
   * @param file file to get the copy in the same branch as context
   * @return a copy of the file that belongs to the same branch as context. 
   * May return input file if it already belongs to the same branch or if context doesn't belong to any branch
   * @throws IllegalArgumentException if file and context already belong to different branches
   */
  public static @NotNull VirtualFile obtainCopyFromTheSameBranch(@NotNull VirtualFile context, @NotNull VirtualFile file) {
    ModelBranch branch = ModelBranch.getFileBranch(context);
    if (branch == null) return file;
    ModelBranch fileBranch = ModelBranch.getFileBranch(file);
    if (fileBranch == branch) return file;
    if (fileBranch != null) {
      throw new IllegalArgumentException("Branch of supplied file differs from context branch");
    }
    return branch.findFileCopy(file);
  } 

  /**
   * @param file virtual file to find the original for
   * @return original file if the supplied file belongs to the branch; supplied file otherwise.
   * May return null for non-null input if the file exists in branch only.
   */
  @Contract("null -> null")
  public static @Nullable VirtualFile findOriginalFile(@Nullable VirtualFile file) {
    if (file == null) return null;
    ModelBranch branch = ModelBranch.getFileBranch(file);
    return branch == null ? file : branch.findOriginalFile(file);
  }

  /**
   * @param psi PSI-element to find the original for
   * @return original PSI element if the supplied element belongs to the branch.
   * May return null for non-null input if the PSI element exists in branch only.
   */
  @Contract("null -> null")
  public static <T extends PsiElement> @Nullable T findOriginalPsi(@Nullable T psi) {
    if (psi == null) return null;
    ModelBranch branch = ModelBranch.getPsiBranch(psi);
    return branch == null ? psi : branch.findOriginalPsi(psi);
  }

  /**
   * Retrieves and removes user-data from element. If the element belongs to the branch
   * then performs the action on original element writing after branch merge.
   * Requires write action for physical elements and read action for branch elements.
   * 
   * @param element element to obtain copyable user data from
   * @param key data key
   * @param <T> type of the data
   * @return found data; null if nothing
   */
  @Nullable
  public static <T> T getAndResetCopyableUserData(PsiElement element, Key<T> key) {
    ModelBranch branch = ModelBranch.getPsiBranch(element);
    PsiElement original = branch == null ? null : branch.findOriginalPsi(element);
    T data = ObjectUtils.notNull(original, element).getCopyableUserData(key);
    if (data != null) {
      if (original != null) {
        branch.runAfterMerge(() -> original.putCopyableUserData(key, null));
      } else {
        element.putCopyableUserData(key, null);
      }
    }
    return data;
  }

  /**
   * Checks that both files belong to the same branch (or both do not belong to any branch)
   * 
   * @param left left file
   * @param right right file
   * @throws IllegalArgumentException if files belong to different branches or one of the file
   * belongs to the branch while other doesn't.
   */
  public static void checkSameBranch(@NotNull VirtualFile left, @NotNull VirtualFile right) {
    ModelBranch leftBranch = ModelBranch.getFileBranch(left);
    ModelBranch rightBranch = ModelBranch.getFileBranch(right);
    if (leftBranch != rightBranch) {
      throw new IllegalArgumentException("Files from different branches: " + leftBranch + " vs " + rightBranch);
    }
  }
}
