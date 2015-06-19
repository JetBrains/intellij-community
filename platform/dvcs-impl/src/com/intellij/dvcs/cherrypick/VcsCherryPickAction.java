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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.impl.VcsLogUtil;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsCherryPickAction extends DumbAwareAction {
  private static final String NAME = "Cherry-Pick";
  @NotNull private final Set<Hash> myIdsInProgress;

  public VcsCherryPickAction() {
    super(NAME, null, DvcsImplIcons.CherryPick);
    myIdsInProgress = ContainerUtil.newHashSet();
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();

    final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    log.requestSelectedDetails(new Consumer<Set<VcsFullCommitDetails>>() {
      @Override
      public void consume(Set<VcsFullCommitDetails> details) {
        ProgressManager.getInstance().run(new CherryPickingTask(project, details));
      }
    }, null);
  }

  /**
   * Sort commits so that earliest ones come first: they need to be cherry-picked first.
   */
  @NotNull
  public static List<VcsFullCommitDetails> sortCommits(@NotNull List<VcsFullCommitDetails> commits) {
    Collections.reverse(commits);
    return commits;
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

    final List<VcsFullCommitDetails> details = VcsLogUtil.collectFirstPackOfLoadedSelectedDetails(log);
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

  private class CherryPickingTask extends Task.Backgroundable {
    private final Project myProject;
    private final ProjectLevelVcsManager myProjectLevelVcsManager;
    private final Map<VcsCherryPicker, List<VcsFullCommitDetails>> myGroupedCommits = ContainerUtil.newHashMap();
    private final Collection<VcsFullCommitDetails> myAllCommits;
    private final ChangeListManagerEx myChangeListManagerEx;

    public CherryPickingTask(@NotNull Project project, @NotNull Set<VcsFullCommitDetails> details) {
      super(project, "Cherry-Picking");
      myProject = project;
      myProjectLevelVcsManager = ProjectLevelVcsManager.getInstance(myProject);
      myAllCommits = details;
      myChangeListManagerEx = (ChangeListManagerEx)ChangeListManager.getInstance(myProject);
      myChangeListManagerEx.blockModalNotifications();
    }

    public boolean processDetails(@NotNull VcsFullCommitDetails details) {
      myIdsInProgress.add(details.getId());

      VcsCherryPicker cherryPicker = getCherryPickerForCommit(myProject, myProjectLevelVcsManager, details);
      if (cherryPicker == null) {
        VcsNotifier.getInstance(myProject).notifyWeakError(
          "Cherry pick is not supported for commit " + details.getId().toShortString() + " from root " + details.getRoot().getName());
        return false;
      }
      List<VcsFullCommitDetails> list = myGroupedCommits.get(cherryPicker);
      if (list == null) {
        myGroupedCommits.put(cherryPicker, list = new ArrayList<VcsFullCommitDetails>()); // ordered set!!
      }
      list.add(details);
      return true;
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        boolean isOk = true;
        for (VcsFullCommitDetails details : myAllCommits) {
          if (!processDetails(details)) {
            isOk = false;
            break;
          }
        }

        if (isOk) {
          for (Map.Entry<VcsCherryPicker, List<VcsFullCommitDetails>> entry : myGroupedCommits.entrySet()) {
            entry.getKey().cherryPick(sortCommits(entry.getValue()));
          }
        }
      }
      finally {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            myChangeListManagerEx.unblockModalNotifications();
            for (VcsFullCommitDetails commit : myAllCommits) {
              myIdsInProgress.remove(commit.getId());
            }
          }
        });
      }
    }
  }
}
