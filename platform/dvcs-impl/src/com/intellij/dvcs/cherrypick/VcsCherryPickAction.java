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
package com.intellij.dvcs.cherrypick;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.vcs.log.*;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsCherryPickAction extends DumbAwareAction {

  private static final String NAME = "Cherry-Pick";
  private static final Logger LOG = Logger.getInstance(VcsCherryPickAction.class);

  @NotNull private final Set<Hash> myIdsInProgress;

  public VcsCherryPickAction() {
    super(NAME, null, DvcsImplIcons.CherryPick);
    myIdsInProgress = ContainerUtil.newHashSet();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);
    final List<VcsFullCommitDetails> commits = log.getSelectedDetails();

    for (VcsFullCommitDetails commit : commits) {
      myIdsInProgress.add(commit.getId());
    }

    FileDocumentManager.getInstance().saveAllDocuments();
    final ChangeListManagerEx changeListManagerEx = (ChangeListManagerEx)ChangeListManager.getInstance(project);
    changeListManagerEx.blockModalNotifications();

    new Task.Backgroundable(project, "Cherry-picking", false) {
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          List<VcsFullCommitDetails> sortedCommits = sortCommits(commits);
          Map<VcsCherryPicker, List<VcsFullCommitDetails>> groupedCommits = groupByVcs(project, sortedCommits);
          for (Map.Entry<VcsCherryPicker, List<VcsFullCommitDetails>> entry : groupedCommits.entrySet()) {
            entry.getKey().cherryPick(entry.getValue());
          }
        }
        finally {
          ApplicationManager.getApplication().invokeLater(new Runnable() {
            public void run() {
              changeListManagerEx.unblockModalNotifications();
              for (VcsFullCommitDetails commit : commits) {
                myIdsInProgress.remove(commit.getId());
              }
            }
          });
        }
      }
    }.queue();
  }

  /**
   * Sort commits so that earliest ones come first: they need to be cherry-picked first.
   */
  @NotNull
  public static List<VcsFullCommitDetails> sortCommits(@NotNull List<VcsFullCommitDetails> commits) {
    Collections.reverse(commits);
    return commits;
  }


  private static Map<VcsCherryPicker, List<VcsFullCommitDetails>> groupByVcs(@NotNull Project project,
                                                                             @NotNull List<VcsFullCommitDetails> commits) {
    final ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
    Map<VcsCherryPicker, List<VcsFullCommitDetails>> resultMap = new HashMap<VcsCherryPicker, List<VcsFullCommitDetails>>();
    for (VcsFullCommitDetails commit : commits) {
      VcsCherryPicker cherryPicker = getCherryPickerForCommit(project, projectLevelVcsManager, commit);
      if (cherryPicker == null) {
        LOG.warn("Cherry pick is not supported for " + commit.getRoot().getName());
        return Collections.emptyMap();
      }
      List<VcsFullCommitDetails> list = resultMap.get(cherryPicker);
      if (list == null) {
        resultMap.put(cherryPicker, list = new ArrayList<VcsFullCommitDetails>()); // ordered set!!
      }
      list.add(commit);
    }
    return resultMap;
  }

  @Nullable
  private static VcsCherryPicker getCherryPickerFor(@NotNull Project project, @NotNull final VcsKey key) {
    return ContainerUtil.find(Extensions.getExtensions(VcsCherryPicker.EXTENSION_POINT_NAME, project), new Condition<VcsCherryPicker>() {
      @Override
      public boolean value(VcsCherryPicker picker) {
        return picker.getSupportedVcs().equals(key);
      }
    });
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(true);
    final VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    Project project = getEventProject(e);
    final List<VcsCherryPicker> cherryPickers = getActiveCherryPickersForProject(project);
    if (log == null || cherryPickers.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    final List<VcsFullCommitDetails> details = log.getSelectedDetails();
    VcsCherryPicker enabledCherryPicker = ContainerUtil.find(cherryPickers, new Condition<VcsCherryPicker>() {
      @Override
      public boolean value(VcsCherryPicker picker) {
        //all commits should be from one vcs, if not then all pickers should return false
        return picker.isEnabled(log, details);
      }
    });
    e.getPresentation().setEnabled(enabledCherryPicker != null);
    e.getPresentation().setText(
      enabledCherryPicker == null ? concatActionNamesForAllAvailable(cherryPickers) : enabledCherryPicker.getActionTitle());
  }

  @NotNull
  private static String concatActionNamesForAllAvailable(@NotNull final List<VcsCherryPicker> pickers) {
    return StringUtil.join(pickers, new Function<VcsCherryPicker, String>() {
      @Override
      public String fun(VcsCherryPicker picker) {
        return picker.getActionTitle();
      }
    }, "/");
  }

  @NotNull
  private static List<VcsCherryPicker> getActiveCherryPickersForProject(@Nullable final Project project) {
    if (project != null) {
      final ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
      AbstractVcs[] vcss = projectLevelVcsManager.getAllActiveVcss();
      return ContainerUtil.mapNotNull(vcss, new Function<AbstractVcs, VcsCherryPicker>() {
        @Override
        public VcsCherryPicker fun(AbstractVcs vcs) {
          return vcs != null ? getCherryPickerFor(project, vcs.getKeyInstanceMethod()) : null;
        }
      });
    }
    return ContainerUtil.emptyList();
  }

  @Nullable
  private static VcsCherryPicker getCherryPickerForCommit(@NotNull Project project,
                                                          @NotNull ProjectLevelVcsManager projectLevelVcsManager,
                                                          @NotNull VcsFullCommitDetails commitDetails) {
    AbstractVcs vcs = projectLevelVcsManager.getVcsFor(commitDetails.getRoot());
    if (vcs == null) return null;
    VcsKey key = vcs.getKeyInstanceMethod();
    return getCherryPickerFor(project, key);
  }
}
