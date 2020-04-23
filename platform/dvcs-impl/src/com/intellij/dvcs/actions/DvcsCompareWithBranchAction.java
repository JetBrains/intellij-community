// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static com.intellij.util.ObjectUtils.chooseNotNull;
import static com.intellij.util.containers.UtilKt.getIfSingle;

/**
 * Compares selected file/folder with itself in another branch.
 */
public abstract class DvcsCompareWithBranchAction<T extends Repository> extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = Objects.requireNonNull(getIfSingle(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM)));

    T repository = Objects.requireNonNull(getRepositoryManager(project).getRepositoryForFileQuick(file));
    assert !repository.isFresh();
    String presentableRevisionName = chooseNotNull(repository.getCurrentBranchName(),
                                                   DvcsUtil.getShortHash(Objects.requireNonNull(repository.getCurrentRevision())));
    List<String> branchNames = getBranchNamesExceptCurrent(repository);

    JBPopupFactory.getInstance()
      .createPopupChooserBuilder(branchNames)
      .setTitle(DvcsBundle.message("popup.title.select.branch.to.compare"))
      .setItemChosenCallback(selected -> showDiffWithBranchUnderModalProgress(project, file, presentableRevisionName, selected))
      .setAutoselectOnMouseMove(true)
      .setNamerForFiltering(o -> o)
      .createPopup()
      .showCenteredInCurrentWindow(project);
  }

  @NotNull
  protected abstract List<String> getBranchNamesExceptCurrent(@NotNull T repository);

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    VirtualFile file = getIfSingle(e.getData(VcsDataKeys.VIRTUAL_FILE_STREAM));

    presentation.setVisible(project != null);
    presentation.setEnabled(project != null && file != null && isEnabled(getRepositoryManager(project).getRepositoryForFileQuick(file)));
  }

  private boolean isEnabled(@Nullable T repository) {
    return repository != null && !repository.isFresh() && !noBranchesToCompare(repository);
  }

  @NotNull
  protected abstract AbstractRepositoryManager<T> getRepositoryManager(@NotNull Project project);

  protected abstract boolean noBranchesToCompare(@NotNull T repository);

  @NotNull
  protected abstract Collection<Change> getDiffChanges(@NotNull Project project, @NotNull VirtualFile file,
                                                       @NotNull String branchToCompare) throws VcsException;

  private void showDiffWithBranchUnderModalProgress(@NotNull final Project project,
                                                    @NotNull final VirtualFile file,
                                                    @NotNull final String head,
                                                    @NotNull final String compare) {
    new Task.Backgroundable(project, DvcsBundle.message("progress.title.collecting.changes"), true) {
      private Collection<Change> changes;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          changes = getDiffChanges(project, file, compare);
        }
        catch (VcsException e) {
          VcsNotifier.getInstance(project).notifyImportantWarning(DvcsBundle.message("notification.title.couldn.t.compare.with.branch"), String
            .format("Couldn't compare " + DvcsUtil.fileOrFolder(file) + " [%s] with branch [%s];\n %s", file, compare, e.getMessage()));
        }
      }

      @Override
      public void onSuccess() {
        //if changes null -> then exception occurred before
        if (changes != null) {
          VcsDiffUtil.showDiffFor(project, changes, VcsDiffUtil.getRevisionTitle(compare, false), VcsDiffUtil.getRevisionTitle(head, true),
                                  VcsUtil.getFilePath(file));
        }
      }
    }.queue();
  }

  protected static String fileDoesntExistInBranchError(@NotNull VirtualFile file, @NotNull String branchToCompare) {
    return String.format("%s <code>%s</code> doesn't exist in branch <code>%s</code>",
                         StringUtil.capitalize(DvcsUtil.fileOrFolder(file)), file.getPresentableUrl(),
                         branchToCompare);
  }
}
