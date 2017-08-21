/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.dvcs.branch;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.repo.AbstractRepositoryManager;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.ui.BranchActionGroupPopup;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.event.HyperlinkEvent;
import java.util.List;

public abstract class DvcsBranchPopup<Repo extends Repository> {
  @NotNull protected final Project myProject;
  @NotNull protected final AbstractRepositoryManager<Repo> myRepositoryManager;
  @NotNull protected final DvcsSyncSettings myVcsSettings;
  @NotNull protected final AbstractVcs myVcs;
  @NotNull protected final DvcsMultiRootBranchConfig<Repo> myMultiRootBranchConfig;

  @NotNull protected final Repo myCurrentRepository;
  @NotNull protected final BranchActionGroupPopup myPopup;
  @NotNull protected final String myRepoTitleInfo;

  protected DvcsBranchPopup(@NotNull Repo currentRepository,
                            @NotNull AbstractRepositoryManager<Repo> repositoryManager,
                            @NotNull DvcsMultiRootBranchConfig<Repo> multiRootBranchConfig,
                            @NotNull DvcsSyncSettings vcsSettings,
                            @NotNull Condition<AnAction> preselectActionCondition, @Nullable String dimensionKey) {
    myProject = currentRepository.getProject();
    myCurrentRepository = currentRepository;
    myRepositoryManager = repositoryManager;
    myVcs = currentRepository.getVcs();
    myVcsSettings = vcsSettings;
    myMultiRootBranchConfig = multiRootBranchConfig;
    String title = myVcs.getDisplayName() + " Branches";
    myRepoTitleInfo = (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC)
                 ? " in " + DvcsUtil.getShortRepositoryName(currentRepository) : "";
    myPopup = new BranchActionGroupPopup(title + myRepoTitleInfo, myProject, preselectActionCondition, createActions(), dimensionKey);
    initBranchSyncPolicyIfNotInitialized();
    warnThatBranchesDivergedIfNeeded();
    if (myRepositoryManager.moreThanOneRoot()) {
      myPopup.addSettingAction(new TrackReposSynchronouslyAction(myVcsSettings));
    }
  }

  @NotNull
  public ListPopup asListPopup() {
    return myPopup;
  }

  private void initBranchSyncPolicyIfNotInitialized() {
    if (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.NOT_DECIDED) {
      if (!myMultiRootBranchConfig.diverged()) {
        notifyAboutSyncedBranches();
        myVcsSettings.setSyncSetting(DvcsSyncSettings.Value.SYNC);
      }
      else {
        myVcsSettings.setSyncSetting(DvcsSyncSettings.Value.DONT_SYNC);
      }
    }
  }

  private void notifyAboutSyncedBranches() {
    String description =
      "You have several " + myVcs.getDisplayName() + " roots in the project and they all are checked out at the same branch. " +
      "We've enabled synchronous branch control for the project. <br/>" +
      "If you wish to control branches in different roots separately, " +
      "you may <a href='settings'>disable</a> the setting.";
    NotificationListener listener = new NotificationListener() {
      @Override
      public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ShowSettingsUtil.getInstance().showSettingsDialog(myProject, myVcs.getConfigurable().getDisplayName());
          if (myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC) {
            notification.expire();
          }
        }
      }
    };
    VcsNotifier.getInstance(myProject).notifyImportantInfo("Synchronous branch control enabled", description, listener);
  }

  @NotNull
  private ActionGroup createActions() {
    DefaultActionGroup popupGroup = new DefaultActionGroup(null, false);
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

  protected abstract void fillWithCommonRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                          @NotNull AbstractRepositoryManager<Repo> repositoryManager);

  @NotNull
  protected List<Repo> filterRepositoriesNotOnThisBranch(@NotNull final String branch,
                                                         @NotNull List<Repo> allRepositories) {
    return ContainerUtil.filter(allRepositories, repository -> !branch.equals(repository.getCurrentBranchName()));
  }

  private void warnThatBranchesDivergedIfNeeded() {
    if (isBranchesDiverged()) {
      myPopup.setWarning("Branches have diverged");
    }
  }

  private boolean isBranchesDiverged() {
    return myRepositoryManager.moreThanOneRoot() && myMultiRootBranchConfig.diverged() && userWantsSyncControl();
  }

  @NotNull
  protected abstract DefaultActionGroup createRepositoriesActions();

  protected abstract void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                                @Nullable DefaultActionGroup actions);

  public static class MyMoreIndex {
    public static final int MAX_NUM = 8;
    public static final int DEFAULT_NUM = 5;
  }

  private static class TrackReposSynchronouslyAction extends ToggleAction implements DumbAware {
    private final DvcsSyncSettings myVcsSettings;

    public TrackReposSynchronouslyAction(@NotNull DvcsSyncSettings vcsSettings) {
      super(DvcsBundle.message("sync.setting"), DvcsBundle.message("sync.setting.description", "repository"), null);
      myVcsSettings = vcsSettings;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.SYNC;
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      myVcsSettings.setSyncSetting(state ? DvcsSyncSettings.Value.SYNC : DvcsSyncSettings.Value.DONT_SYNC);
    }
  }
}
