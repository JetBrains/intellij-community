// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.cherrypick;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.VcsFullCommitDetails;
import com.intellij.vcs.log.VcsLog;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.openapi.vcs.VcsNotificationIdsHolder.CHERRY_PICK_ERROR;

@Service(Service.Level.PROJECT)
public final class VcsCherryPickManager {
  private static final Logger LOG = Logger.getInstance(VcsCherryPickManager.class);
  private final @NotNull Project myProject;
  private final @NotNull Set<CommitId> myIdsInProgress = ContainerUtil.newConcurrentSet();
  private final @NotNull BackgroundTaskQueue myTaskQueue;

  public VcsCherryPickManager(@NotNull Project project) {
    myProject = project;
    myTaskQueue = new BackgroundTaskQueue(project, DvcsBundle.message("cherry.picking.process"));
  }

  public void cherryPick(@NotNull VcsLog log) {
    log.requestSelectedDetails( details -> myTaskQueue.run(new CherryPickingTask(ContainerUtil.reverse(details))));
  }

  public boolean isCherryPickAlreadyStartedFor(@NotNull List<? extends CommitId> commits) {
    for (CommitId commit : commits) {
      if (myIdsInProgress.contains(commit)) {
        return true;
      }
    }
    return false;
  }

  private @Nullable VcsCherryPicker getCherryPickerForCommit(@NotNull VcsFullCommitDetails commitDetails) {
    AbstractVcs vcs = ProjectLevelVcsManager.getInstance(myProject).getVcsFor(commitDetails.getRoot());
    if (vcs == null) return null;
    VcsKey key = vcs.getKeyInstanceMethod();
    return getCherryPickerFor(key);
  }

  public @Nullable VcsCherryPicker getCherryPickerFor(final @NotNull VcsKey key) {
    return ContainerUtil.find(VcsCherryPicker.EXTENSION_POINT_NAME.getExtensions(myProject),
                              picker -> picker.getSupportedVcs().equals(key));
  }

  private final class CherryPickingTask extends Task.Backgroundable {
    private final @NotNull List<? extends VcsFullCommitDetails> myAllDetailsInReverseOrder;
    private final @NotNull ChangeListManagerEx myChangeListManager;

    CherryPickingTask(@NotNull List<? extends VcsFullCommitDetails> detailsInReverseOrder) {
      super(VcsCherryPickManager.this.myProject, DvcsBundle.message("cherry.picking.process"));
      myAllDetailsInReverseOrder = detailsInReverseOrder;
      myChangeListManager = ChangeListManagerEx.getInstanceEx(myProject);
      myChangeListManager.blockModalNotifications();
    }

    private @Nullable VcsCherryPicker getCherryPickerOrReportError(@NotNull VcsFullCommitDetails details) {
      CommitId commitId = new CommitId(details.getId(), details.getRoot());
      if (myIdsInProgress.contains(commitId)) {
        showError(DvcsBundle.message("cherry.pick.process.is.already.started.for.commit.from.root",
                                     commitId.getHash().toShortString(),
                                     commitId.getRoot().getName()));
        return null;
      }
      myIdsInProgress.add(commitId);

      VcsCherryPicker cherryPicker = getCherryPickerForCommit(details);
      if (cherryPicker == null) {
        showError(DvcsBundle.message("cherry.pick.is.not.supported.for.commit.from.root",
                                     details.getId().toShortString(),
                                     details.getRoot().getName()));
        return null;
      }
      return cherryPicker;
    }

    public void showError(@Nls @NotNull String message) {
      VcsNotifier.getInstance(myProject).notifyWeakError(CHERRY_PICK_ERROR, message);
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
            entry.getKey().cherryPick(new ArrayList<>(entry.getValue()));
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

    public @NotNull MultiMap<VcsCherryPicker, VcsFullCommitDetails> createArrayMultiMap() {
      return new MultiMap<>() {
        @Override
        protected @NotNull Collection<VcsFullCommitDetails> createCollection() {
          return new ArrayList<>();
        }
      };
    }
  }

  public static VcsCherryPickManager getInstance(@NotNull Project project) {
    return project.getService(VcsCherryPickManager.class);
  }
}
