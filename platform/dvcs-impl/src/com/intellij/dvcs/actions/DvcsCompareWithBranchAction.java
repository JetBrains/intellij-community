// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.actions;

import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Compares selected file/folder with itself in another branch.
 */
public abstract class DvcsCompareWithBranchAction<T extends Repository> extends DvcsCompareWithAction<T> {
  @NotNull
  protected abstract List<String> getBranchNamesExceptCurrent(@NotNull T repository);

  @NotNull
  protected abstract Collection<Change> getDiffChanges(@NotNull Project project, @NotNull VirtualFile file,
                                                       @NotNull String branchToCompare) throws VcsException;


  @Override
  protected @NotNull JBPopup createPopup(@NotNull Project project, @NotNull T repository, @NotNull VirtualFile file) {
    String presentableRevisionName = getPresentableCurrentBranchName(repository);
    List<String> branchNames = getBranchNamesExceptCurrent(repository);

    return createPopup(DvcsBundle.message("popup.title.select.branch.to.compare"), branchNames,
                       selected -> showDiffWithBranchUnderModalProgress(project, file, presentableRevisionName, selected));
  }

  private void showDiffWithBranchUnderModalProgress(@NotNull final Project project,
                                                    @NotNull final VirtualFile file,
                                                    @NotNull final @NlsSafe String head,
                                                    @NotNull final @NlsSafe String compare) {
    String revNumTitle1 = VcsDiffUtil.getRevisionTitle(compare, false);
    String revNumTitle2 = VcsDiffUtil.getRevisionTitle(head, true);
    showDiffBetweenRevision(project, file, revNumTitle1, revNumTitle2, () -> getDiffChanges(project, file, compare));
  }

  @NlsSafe
  protected static String fileDoesntExistInBranchError(@NotNull VirtualFile file, @NotNull String branchToCompare) {
    return DvcsBundle.message("error.text.file.not.found.in.branch",
                              file.isDirectory() ? 2 : 1, file.getPresentableUrl(), branchToCompare);
  }
}
