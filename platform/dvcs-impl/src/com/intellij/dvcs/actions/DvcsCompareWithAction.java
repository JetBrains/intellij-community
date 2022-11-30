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
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer;
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.containers.JBIterable;
import com.intellij.vcs.CompareWithLocalDialog;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

/**
 * Compares selected file/folder with itself in another revision.
 */
public abstract class DvcsCompareWithAction<T extends Repository> extends DumbAwareAction {
  @NotNull
  protected abstract AbstractRepositoryManager<T> getRepositoryManager(@NotNull Project project);

  protected abstract boolean nothingToCompare(@NotNull T repository);

  @Nullable
  protected abstract JBPopup createPopup(@NotNull Project project, @NotNull T repository, @NotNull VirtualFile file);


  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VirtualFile file = Objects.requireNonNull(JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).single());

    T repository = Objects.requireNonNull(getRepositoryManager(project).getRepositoryForFileQuick(file));
    assert !repository.isFresh();

    JBPopup popup = createPopup(project, repository, file);
    if (popup == null) return;

    ApplicationManager.getApplication().invokeLater(() -> {
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> popup.showCenteredInCurrentWindow(project));
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    Project project = e.getProject();
    VirtualFile file = JBIterable.from(e.getData(VcsDataKeys.VIRTUAL_FILES)).single();

    presentation.setVisible(project != null);
    presentation.setEnabled(project != null && file != null && isEnabled(getRepositoryManager(project).getRepositoryForFileQuick(file)));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private boolean isEnabled(@Nullable T repository) {
    return repository != null && !repository.isFresh() && !nothingToCompare(repository);
  }

  @NotNull
  protected static JBPopup createPopup(@NotNull @NlsContexts.PopupTitle String title,
                                       @NotNull List<String> options,
                                       @NotNull Consumer<? super String> onChosen) {
    return JBPopupFactory.getInstance()
      .createPopupChooserBuilder(options)
      .setTitle(title)
      .setItemChosenCallback(onChosen::accept)
      .setAutoselectOnMouseMove(true)
      .setNamerForFiltering(o -> o)
      .createPopup();
  }

  protected static void showDiffBetweenRevision(@NotNull Project project,
                                                @NotNull VirtualFile file,
                                                @NotNull @Nls String revNumTitle1,
                                                @NotNull @Nls String revNumTitle2,
                                                @NotNull ThrowableComputable<? extends Collection<Change>, ? extends VcsException> changesLoader) {
    if (file.isDirectory()) {
      String dialogTitle = VcsBundle.message("history.dialog.title.difference.between.versions.in",
                                             revNumTitle1, revNumTitle2, file.getName());
      CompareWithLocalDialog.showChanges(project, dialogTitle, CompareWithLocalDialog.LocalContent.AFTER, changesLoader);
    }
    else {
      DiffRequestChain requestChain = new ChangeDiffRequestChain.Async() {
        @Override
        protected @NotNull ListSelection<ChangeDiffRequestProducer> loadRequestProducers() throws DiffRequestProducerException {
          try {
            Collection<Change> changes = changesLoader.compute();

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

  @NotNull
  protected static String getPresentableCurrentBranchName(Repository repository) {
    String branchName = repository.getCurrentBranchName();
    if (branchName != null) return branchName;

    String revision = repository.getCurrentRevision();
    if (revision != null) return DvcsUtil.getShortHash(revision);

    return VcsBundle.message("diff.title.local");
  }
}
