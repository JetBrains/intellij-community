/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.google.common.collect.Lists;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.ChangeListManager;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsCherryPickManager {
  private static final Logger LOG = Logger.getInstance(VcsCherryPickManager.class);
  @NotNull private final Project myProject;
  @NotNull private final ProjectLevelVcsManager myProjectLevelVcsManager;
  @NotNull private final Set<CommitId> myIdsInProgress = ContainerUtil.newConcurrentSet();
  @NotNull private final BackgroundTaskQueue myTaskQueue;

  public VcsCherryPickManager(@NotNull Project project, @NotNull ProjectLevelVcsManager projectLevelVcsManager) {
    myProject = project;
    myProjectLevelVcsManager = projectLevelVcsManager;
    myTaskQueue = new BackgroundTaskQueue(project, "Cherry-picking");
  }

  public void cherryPick(@NotNull VcsLog log) {
    log.requestSelectedDetails( details -> myTaskQueue.run(new CherryPickingTask(ContainerUtil.reverse(details))));
  }

  public boolean isCherryPickAlreadyStartedFor(@NotNull List<CommitId> commits) {
    for (CommitId commit : commits) {
      if (myIdsInProgress.contains(commit)) {
        return true;
      }
    }
    return false;
  }

  @Nullable
  private VcsCherryPicker getCherryPickerForCommit(@NotNull VcsFullCommitDetails commitDetails) {
    AbstractVcs vcs = myProjectLevelVcsManager.getVcsFor(commitDetails.getRoot());
    if (vcs == null) return null;
    VcsKey key = vcs.getKeyInstanceMethod();
    return getCherryPickerFor(key);
  }

  @Nullable
  public VcsCherryPicker getCherryPickerFor(@NotNull final VcsKey key) {
    return ContainerUtil.find(Extensions.getExtensions(VcsCherryPicker.EXTENSION_POINT_NAME, myProject),
                              picker -> picker.getSupportedVcs().equals(key));
  }

  private class CherryPickingTask extends Task.Backgroundable {
    @NotNull private final List<VcsFullCommitDetails> myAllDetailsInReverseOrder;
    @NotNull private final ChangeListManagerEx myChangeListManager;

    public CherryPickingTask(@NotNull List<VcsFullCommitDetails> detailsInReverseOrder) {
      super(VcsCherryPickManager.this.myProject, "Cherry-Picking");
      myAllDetailsInReverseOrder = detailsInReverseOrder;
      myChangeListManager = (ChangeListManagerEx)ChangeListManager.getInstance(myProject);
      myChangeListManager.blockModalNotifications();
    }

    @Nullable
    private VcsCherryPicker getCherryPickerOrReportError(@NotNull VcsFullCommitDetails details) {
      CommitId commitId = new CommitId(details.getId(), details.getRoot());
      if (myIdsInProgress.contains(commitId)) {
        showError("Cherry pick process is already started for commit " +
                  commitId.getHash().toShortString() +
                  " from root " +
                  commitId.getRoot().getName());
        return null;
      }
      myIdsInProgress.add(commitId);

      VcsCherryPicker cherryPicker = getCherryPickerForCommit(details);
      if (cherryPicker == null) {
        showError(
          "Cherry pick is not supported for commit " + details.getId().toShortString() + " from root " + details.getRoot().getName());
        return null;
      }
      return cherryPicker;
    }

    public void showError(@NotNull String message) {
      VcsNotifier.getInstance(myProject).notifyWeakError(message);
      LOG.warn(message);
    }

    @Override
    public void run(@NotNull ProgressIndicator indicator) {
      try {
        boolean isOk = true;
        MultiMap<VcsCherryPicker, VcsFullCommitDetails> groupedCommits = createArrayMultiMap();
        for (VcsFullCommitDetails details : myAllDetailsInReverseOrder) {
          VcsCherryPicker cherryPicker = getCherryPickerOrReportError(details);
          if (cherryPicker == null) {
            isOk = false;
            break;
          }
          groupedCommits.putValue(cherryPicker, details);
        }

        if (isOk) {
          for (Map.Entry<VcsCherryPicker, Collection<VcsFullCommitDetails>> entry : groupedCommits.entrySet()) {
            entry.getKey().cherryPick(Lists.newArrayList(entry.getValue()));
          }
        }
      }
      finally {
        ApplicationManager.getApplication().invokeLater(() -> {
          myChangeListManager.unblockModalNotifications();
          for (VcsFullCommitDetails details : myAllDetailsInReverseOrder) {
            myIdsInProgress.remove(new CommitId(details.getId(), details.getRoot()));
          }
        });
      }
    }

    @NotNull
    public MultiMap<VcsCherryPicker, VcsFullCommitDetails> createArrayMultiMap() {
      return new MultiMap<VcsCherryPicker, VcsFullCommitDetails>() {
        @NotNull
        @Override
        protected Collection<VcsFullCommitDetails> createCollection() {
          return new ArrayList<>();
        }
      };
    }
  }

  public static VcsCherryPickManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, VcsCherryPickManager.class);
  }
}
