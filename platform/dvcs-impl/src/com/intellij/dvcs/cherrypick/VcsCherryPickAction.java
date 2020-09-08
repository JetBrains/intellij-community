// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.cherrypick;

import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.CommitId;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsLog;
import com.intellij.vcs.log.VcsLogDataKeys;
import com.intellij.vcs.log.util.VcsLogUtil;
import icons.DvcsImplIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class VcsCherryPickAction extends DumbAwareAction {
  public VcsCherryPickAction() {
    super(DvcsBundle.messagePointer("cherry.pick.action.text"),
          DvcsBundle.messagePointer("cherry.pick.action.description"),
          DvcsImplIcons.CherryPick);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    FileDocumentManager.getInstance().saveAllDocuments();

    Project project = e.getRequiredData(CommonDataKeys.PROJECT);
    VcsLog log = e.getRequiredData(VcsLogDataKeys.VCS_LOG);

    VcsCherryPickManager.getInstance(project).cherryPick(log);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    e.getPresentation().setVisible(true);

    final VcsLog log = e.getData(VcsLogDataKeys.VCS_LOG);
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    VcsCherryPickManager cherryPickManager = VcsCherryPickManager.getInstance(project);

    List<VcsCherryPicker> cherryPickers = getActiveCherryPickersForProject(project);
    if (log == null || cherryPickers.isEmpty()) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    List<CommitId> commits = ContainerUtil.getFirstItems(log.getSelectedCommits(), VcsLogUtil.MAX_SELECTED_COMMITS);
    if (commits.isEmpty() || cherryPickManager.isCherryPickAlreadyStartedFor(commits)) {
      e.getPresentation().setEnabled(false);
      return;
    }

    final Map<VirtualFile, List<Hash>> groupedByRoot = groupByRoot(commits);
    VcsCherryPicker activeCherryPicker = getActiveCherryPicker(cherryPickers, groupedByRoot.keySet());
    e.getPresentation().setEnabled(activeCherryPicker != null);
    e.getPresentation()
      .setText(activeCherryPicker == null ? concatActionNamesForAllAvailable(cherryPickers) : activeCherryPicker.getActionTitle());
    e.getPresentation().setDescription(activeCherryPicker != null ? "" : DvcsBundle.message("cherry.pick.action.description"));
  }

  @Nullable
  private static VcsCherryPicker getActiveCherryPicker(@NotNull List<VcsCherryPicker> cherryPickers,
                                                       @NotNull Collection<VirtualFile> roots) {
    return ContainerUtil.find(cherryPickers, picker -> picker.canHandleForRoots(roots));
  }

  @NotNull
  private static Map<VirtualFile, List<Hash>> groupByRoot(@NotNull List<CommitId> details) {
    Map<VirtualFile, List<Hash>> result = new HashMap<>();
    for (CommitId commit : details) {
      List<Hash> hashes = result.get(commit.getRoot());
      if (hashes == null) {
        hashes = new ArrayList<>();
        result.put(commit.getRoot(), hashes);
      }
      hashes.add(commit.getHash());
    }
    return result;
  }

  @Nls
  @NotNull
  private static String concatActionNamesForAllAvailable(@NotNull final List<VcsCherryPicker> pickers) {
    return StringUtil.join(pickers, VcsCherryPicker::getActionTitle, "/");
  }

  @NotNull
  private static List<VcsCherryPicker> getActiveCherryPickersForProject(@Nullable final Project project) {
    if (project != null) {
      final ProjectLevelVcsManager projectLevelVcsManager = ProjectLevelVcsManager.getInstance(project);
      AbstractVcs[] vcss = projectLevelVcsManager.getAllActiveVcss();
      return ContainerUtil.mapNotNull(vcss, vcs -> vcs != null ? VcsCherryPickManager.getInstance(project)
        .getCherryPickerFor(vcs.getKeyInstanceMethod()) : null);
    }
    return ContainerUtil.emptyList();
  }


}
