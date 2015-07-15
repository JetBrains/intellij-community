/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import java.util.List;

public abstract class DvcsBranchPopup<Repo extends Repository> {
  @NotNull protected final Project myProject;
  @NotNull protected final AbstractRepositoryManager<Repo> myRepositoryManager;
  @NotNull protected final DvcsSyncSettings myVcsSettings;
  @NotNull protected final AbstractVcs myVcs;
  @NotNull protected final DvcsMultiRootBranchConfig<Repo> myMultiRootBranchConfig;

  @NotNull protected final Repo myCurrentRepository;
  @NotNull protected final ListPopupImpl myPopup;

  protected DvcsBranchPopup(@NotNull Repo currentRepository,
                            @NotNull AbstractRepositoryManager<Repo> repositoryManager,
                            @NotNull DvcsMultiRootBranchConfig<Repo> multiRootBranchConfig,
                            @NotNull DvcsSyncSettings vcsSettings,
                            @NotNull Condition<AnAction> preselectActionCondition) {
    myProject = currentRepository.getProject();
    myCurrentRepository = currentRepository;
    myRepositoryManager = repositoryManager;
    myVcs = currentRepository.getVcs();
    myVcsSettings = vcsSettings;
    myMultiRootBranchConfig = multiRootBranchConfig;
    String title = createPopupTitle(currentRepository);
    myPopup = new BranchActionGroupPopup(title, myProject, preselectActionCondition, createActions());

    initBranchSyncPolicyIfNotInitialized();
    setCurrentBranchInfo();
    warnThatBranchesDivergedIfNeeded();
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

  @NotNull
  private String createPopupTitle(@NotNull Repo currentRepository) {
    String title = myVcs.getDisplayName() + " Branches";
    if (myRepositoryManager.moreThanOneRoot() && myVcsSettings.getSyncSetting() == DvcsSyncSettings.Value.DONT_SYNC) {
      title += " in " + DvcsUtil.getShortRepositoryName(currentRepository);
    }
    return title;
  }

  protected void setCurrentBranchInfo() {
    String branchText = "Current branch : ";
    myPopup.setAdText(branchText + myCurrentRepository.getCurrentBranchName(), SwingConstants.CENTER);
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

  private boolean userWantsSyncControl() {
    return (myVcsSettings.getSyncSetting() != DvcsSyncSettings.Value.DONT_SYNC);
  }

  protected abstract void fillWithCommonRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                          @NotNull AbstractRepositoryManager<Repo> repositoryManager);

  @NotNull
  protected List<Repo> filterRepositoriesNotOnThisBranch(@NotNull final String branch,
                                                         @NotNull List<Repo> allRepositories) {
    return ContainerUtil.filter(allRepositories, new Condition<Repo>() {
      @Override
      public boolean value(Repo repository) {
        return !branch.equals(repository.getCurrentBranchName());
      }
    });
  }

  private void warnThatBranchesDivergedIfNeeded() {
    if (myRepositoryManager.moreThanOneRoot() && myMultiRootBranchConfig.diverged() && userWantsSyncControl()) {
      myPopup.setWarning("Branches have diverged");
    }
  }

  @NotNull
  protected abstract DefaultActionGroup createRepositoriesActions();

  protected boolean highlightCurrentRepo() {
    return !userWantsSyncControl() || myMultiRootBranchConfig.diverged();
  }

  protected abstract void fillPopupWithCurrentRepositoryActions(@NotNull DefaultActionGroup popupGroup,
                                                                @Nullable DefaultActionGroup actions);
}
