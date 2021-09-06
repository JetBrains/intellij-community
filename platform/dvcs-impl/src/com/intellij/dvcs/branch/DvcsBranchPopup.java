// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.BranchActionGroupPopup;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.dvcs.ui.LightActionGroup;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.ui.ExperimentalUI;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.openapi.vcs.VcsNotifier.STANDARD_NOTIFICATION;

public abstract class DvcsBranchPopup<Repo extends Repository> {
  @NotNull protected final Project myProject;
  @NotNull protected final AbstractRepositoryManager<Repo> myRepositoryManager;
  @NotNull protected final DvcsSyncSettings myVcsSettings;
  @NotNull protected final AbstractVcs myVcs;
  @NotNull protected final DvcsMultiRootBranchConfig<Repo> myMultiRootBranchConfig;

  @NotNull protected final Repo myCurrentRepository;
  @NotNull protected final BranchActionGroupPopup myPopup;
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
    initBranchSyncPolicyIfNotInitialized();
    warnThatBranchesDivergedIfNeeded();
    if (myRepositoryManager.moreThanOneRoot()) {
      myPopup.addToolbarAction(new TrackReposSynchronouslyAction(myVcsSettings), true);
    }
  }

  @Nullable
  private @Nls String buildTitle(@NotNull Repo currentRepository) {
    if (ExperimentalUI.isNewVcsBranchPopup()) return null;

    String vcsName = myVcs.getDisplayName();
    return myInSpecificRepository ?
           DvcsBundle.message("branch.popup.vcs.name.branches", vcsName) :
           DvcsBundle.message("branch.popup.vcs.name.branches.in.repo", vcsName, DvcsUtil.getShortRepositoryName(currentRepository));
  }

  @NotNull
  public ListPopup asListPopup() {
    return myPopup;
  }

  private void initBranchSyncPolicyIfNotInitialized() {
    if (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.NOT_DECIDED) {
      if (myRepositoryManager.shouldProposeSyncControl()) {
        notifyAboutSyncedBranches();
        myVcsSettings.setSyncSetting(DvcsSyncSettings.Value.SYNC);
      }
      else {
        myVcsSettings.setSyncSetting(DvcsSyncSettings.Value.DONT_SYNC);
      }
    }
  }

  private void notifyAboutSyncedBranches() {
    VcsNotifier.getInstance(myProject).notify(
      STANDARD_NOTIFICATION
        .createNotification(DvcsBundle.message("notification.message.branch.operations.are.executed.on.all.roots"), NotificationType.INFORMATION)
        .setDisplayId("vcs.branch.operations.are.executed.on.all.roots")
        .addAction(
          NotificationAction.create(DvcsBundle.message("action.NotificationAction.DvcsBranchPopup.text.disable"), (event, notification) -> {
            ShowSettingsUtil.getInstance().showSettingsDialog(myProject, myVcs.getDisplayName());
            if (myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC) {
              notification.expire();
            }
          })));
  }

  @NotNull
  private ActionGroup createActions() {
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

  @NotNull
  protected List<Repo> filterRepositoriesNotOnThisBranch(@NotNull final String branch,
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

  @NotNull
  protected abstract LightActionGroup createRepositoriesActions();

  protected abstract void fillPopupWithCurrentRepositoryActions(@NotNull LightActionGroup popupGroup,
                                                                @Nullable LightActionGroup actions);

  public static final class MyMoreIndex {
    public static final int MAX_NUM = 8;
    public static final int DEFAULT_NUM = 5;
  }

  private static class TrackReposSynchronouslyAction extends ToggleAction implements DumbAware {
    private final DvcsSyncSettings myVcsSettings;

    TrackReposSynchronouslyAction(@NotNull DvcsSyncSettings vcsSettings) {
      super(DvcsBundle.message("sync.setting"), DvcsBundle.message("sync.setting.description", VcsBundle.message("vcs.generic.name")), null);
      myVcsSettings = vcsSettings;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      return myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      myVcsSettings.setSyncSetting(state ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    }
  }
}
