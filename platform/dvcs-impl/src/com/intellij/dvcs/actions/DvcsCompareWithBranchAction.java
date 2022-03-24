// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.actions;

import com.intellij.diff.DiffDialogHints;
import com.intellij.diff.DiffManager;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.util.DiffUserDataKeysEx;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vcs.history.VcsDiffUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcs.CompareWithLocalDialog;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.ObjectUtils.chooseNotNull;

/**
 * Compares selected file/folder with itself in another branch.
 */
public abstract class DvcsCompareWithBranchAction<T extends Repository> extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = Objects.requireNonNull(JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).single());

    T repository = Objects.requireNonNull(getRepositoryManager(project).getRepositoryForFileQuick(file));
    assert !repository.isFresh();
    String presentableRevisionName = chooseNotNull(repository.getCurrentBranchName(),
                                                   DvcsUtil.getShortHash(Objects.requireNonNull(repository.getCurrentRevision())));
    List<String> branchNames = getBranchNamesExceptCurrent(repository);

    JBPopup popup = JBPopupFactory.getInstance()
      .createPopupChooserBuilder(branchNames)
      .setTitle(DvcsBundle.message("popup.title.select.branch.to.compare"))
      .setItemChosenCallback(selected -> showDiffWithBranchUnderModalProgress(project, file, presentableRevisionName, selected))
      .setAutoselectOnMouseMove(true)
      .setNamerForFiltering(o -> o)
      .createPopup();

    ApplicationManager.getApplication().invokeLater(() -> {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> popup.showCenteredInCurrentWindow(project));
    });
  }

  @NotNull
  protected abstract List<String> getBranchNamesExceptCurrent(@NotNull T repository);

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    VirtualFile file = JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).single();

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
                                                    @NotNull final @NlsSafe String head,
                                                    @NotNull final @NlsSafe String compare) {
    FilePath filePath = VcsUtil.getFilePath(file);
    String revNumTitle1 = VcsDiffUtil.getRevisionTitle(compare, false);
    String revNumTitle2 = VcsDiffUtil.getRevisionTitle(head, true);

    if (file.isDirectory()) {
      String dialogTitle = VcsBundle.message("history.dialog.title.difference.between.versions.in",
                                             revNumTitle1, revNumTitle2, filePath.getName());
      CompareWithLocalDialog.showChanges(project, dialogTitle, CompareWithLocalDialog.LocalContent.AFTER, () -> {
        return getDiffChanges(project, file, compare);
      });
    }
    else {
      DiffRequestChain requestChain = new ChangeDiffRequestChain.Async() {
        @Override
        protected @NotNull ListSelection<ChangeDiffRequestProducer> loadRequestProducers() throws DiffRequestProducerException {
          try {
            Collection<Change> changes = getDiffChanges(project, file, compare);

            Map<Key<?>, Object> changeContext = new HashMap<>(2);
            changeContext.put(DiffUserDataKeysEx.VCS_DIFF_LEFT_CONTENT_TITLE, revNumTitle1);
            changeContext.put(DiffUserDataKeysEx.VCS_DIFF_RIGHT_CONTENT_TITLE, revNumTitle2);

            return ListSelection.createAt(new ArrayList<>(changes), 0)
              .map(change -> ChangeDiffRequestProducer.create(project, change, changeContext));
          }
          catch (VcsException e) {
            throw new DiffRequestProducerException(e);
          }
        }
      };
      DiffManager.getInstance().showDiff(project, requestChain, DiffDialogHints.DEFAULT);
    }
  }

  @NlsSafe
  protected static String fileDoesntExistInBranchError(@NotNull VirtualFile file, @NotNull String branchToCompare) {
    return DvcsBundle.message("error.text.file.not.found.in.branch",
                              file.isDirectory() ? 1 : 0, file.getPresentableUrl(), branchToCompare);
  }
}
