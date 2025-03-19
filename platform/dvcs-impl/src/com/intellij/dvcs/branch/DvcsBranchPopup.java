// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.BranchActionGroupPopup;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;

public abstract class DvcsBranchPopup<Repo extends Repository> {
  protected final @NotNull Project myProject;
  protected final @NotNull AbstractRepositoryManager<Repo> myRepositoryManager;
  protected final @NotNull DvcsSyncSettings myVcsSettings;
  protected final @NotNull AbstractVcs myVcs;
  protected final @NotNull DvcsMultiRootBranchConfig<Repo> myMultiRootBranchConfig;

  protected final @NotNull Repo myCurrentRepository;
  protected final @NotNull BranchActionGroupPopup myPopup;
  protected final boolean myInSpecificRepository;

  protected DvcsBranchPopup(@NotNull Repo currentRepository,
                            @NotNull AbstractRepositoryManager<Repo> repositoryManager,
                            @NotNull DvcsMultiRootBranchConfig<Repo> multiRootBranchConfig,
                            @NotNull DvcsSyncSettings vcsSettings,
                            @NotNull Condition<AnAction> preselectActionCondition,
                            @Nullable String dimensionKey,
                            @NotNull DataContext dataContext) {
    myProject = currentRepository.getProject();
    myCurrentRepository = currentRepository;
    myRepositoryManager = repositoryManager;
    myVcs = currentRepository.getVcs();
    myVcsSettings = vcsSettings;
    myMultiRootBranchConfig = multiRootBranchConfig;
    myInSpecificRepository = myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC;
    String title = buildTitle(currentRepository);
    myPopup = new BranchActionGroupPopup(title, myProject, preselectActionCondition, createActions(), dimensionKey, dataContext);
    new DvcsBranchSyncPolicyUpdateNotifier<>(myProject, myVcs, myVcsSettings, myRepositoryManager).initBranchSyncPolicyIfNotInitialized();
    warnThatBranchesDivergedIfNeeded();
    if (myRepositoryManager.moreThanOneRoot()) {
      myPopup.addToolbarAction(new DefaultTrackReposSynchronouslyAction(myVcsSettings), true);
    }
  }

  private @Nullable @Nls String buildTitle(@NotNull Repo currentRepository) {
    if (ExperimentalUI.isNewUI()) return null;

    String vcsName = myVcs.getDisplayName();
    return myInSpecificRepository ?
           DvcsBundle.message("branch.popup.vcs.name.branches", vcsName) :
           DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", vcsName, DvcsUtil.getShortRepositoryName(currentRepository));
  }

  public @NotNull ListPopup asListPopup() {
    return myPopup;
  }

  private @NotNull ActionGroup createActions() {
    LightActionGroup popupGroup = new LightActionGroup(false);
    AbstractRepositoryManager<Repo> repositoryManager = myRepositoryManager;
    if (repositoryManager.moreThanOneRoot()) {
      if (userWantsSyncControl()) {
        fillWithCommonRepositoryActions(popupGroup, repositoryManager);
      }
      else {
        fillPopupWithCurrentRepositoryActions(popupGroup, createRepositoriesActions());
      }
    }
    else {
      fillPopupWithCurrentRepositoryActions(popupGroup, null);
    }
    popupGroup.addSeparator();
    return popupGroup;
  }

  protected boolean userWantsSyncControl() {
    return (myVcsSettings.getSyncSetting() != DvcsSyncSettings.Value.DONT_SYNC);
  }

  protected abstract void fillWithCommonRepositoryActions(@NotNull LightActionGroup popupGroup,
                                                          @NotNull AbstractRepositoryManager<Repo> repositoryManager);

  protected @NotNull @Unmodifiable List<Repo> filterRepositoriesNotOnThisBranch(final @NotNull String branch,
                                                                                @NotNull List<? extends Repo> allRepositories) {
    return ContainerUtil.filter(allRepositories, repository -> !branch.equals(repository.getCurrentBranchName()));
  }

  private void warnThatBranchesDivergedIfNeeded() {
    if (isBranchesDiverged()) {
      myPopup.setWarning(DvcsBundle.message("branch.popup.warning.branches.have.diverged"));
    }
  }

  private boolean isBranchesDiverged() {
    return myRepositoryManager.moreThanOneRoot() && myMultiRootBranchConfig.diverged() && userWantsSyncControl();
  }

  protected abstract @NotNull LightActionGroup createRepositoriesActions();

  protected abstract void fillPopupWithCurrentRepositoryActions(@NotNull LightActionGroup popupGroup,
                                                                @Nullable LightActionGroup actions);

  public static final class MyMoreIndex {
    public static final int MAX_NUM = 8;
    public static final int DEFAULT_NUM = 5;
  }

  private static class DefaultTrackReposSynchronouslyAction extends TrackReposSynchronouslyAction {
    private final DvcsSyncSettings myVcsSettings;

    protected DefaultTrackReposSynchronouslyAction(@NotNull DvcsSyncSettings vcsSettings) {
      myVcsSettings = vcsSettings;
    }

    @Override
    protected @NotNull DvcsSyncSettings getSettings(@NotNull AnActionEvent e) {
      return myVcsSettings;
    }
  }
}
