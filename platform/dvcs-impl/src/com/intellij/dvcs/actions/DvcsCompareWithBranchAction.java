// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
  protected abstract @NotNull List<String> getBranchNamesExceptCurrent(@NotNull T repository);

  protected abstract @NotNull Collection<Change> getDiffChanges(@NotNull Project project, @NotNull VirtualFile file,
                                                                @NotNull String branchToCompare) throws VcsException;


  @Override
  protected @NotNull JBPopup createPopup(@NotNull Project project, @NotNull T repository, @NotNull VirtualFile file) {
    String presentableRevisionName = getPresentableCurrentBranchName(repository);
    List<String> branchNames = getBranchNamesExceptCurrent(repository);

    return createPopup(DvcsBundle.message("popup.title.select.branch.to.compare"), branchNames,
                       selected -> showDiffWithBranchUnderModalProgress(project, file, presentableRevisionName, selected));
  }

  private void showDiffWithBranchUnderModalProgress(final @NotNull Project project,
                                                    final @NotNull VirtualFile file,
                                                    final @NotNull @NlsSafe String head,
                                                    final @NotNull @NlsSafe String compare) {
    String revNumTitle1 = VcsDiffUtil.getRevisionTitle(compare, false);
    String revNumTitle2 = VcsDiffUtil.getRevisionTitle(head, true);
    showDiffBetweenRevision(project, file, revNumTitle1, revNumTitle2, () -> getDiffChanges(project, file, compare));
  }

  protected static @NlsSafe String fileDoesntExistInBranchError(@NotNull VirtualFile file, @NotNull String branchToCompare) {
    return DvcsBundle.message("error.text.file.not.found.in.branch",
                              file.isDirectory() ? 2 : 1, file.getPresentableUrl(), branchToCompare);
  }
}
