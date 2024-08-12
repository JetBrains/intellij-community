// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author gregsh
 */
public final class DetachDirectoryAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    ProjectFileIndex index = project == null ? null : ProjectFileIndex.getInstance(project);
    JBIterable<VirtualFile> files = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY));
    boolean enabled = index != null &&
                      files.isNotEmpty() &&
                      files.filter(o -> !o.equals(index.getContentRootForFile(o))).isEmpty();
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      e.getPresentation().setText(getTitle(files.take(2).size()));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ProjectFileIndex index = ProjectFileIndex.getInstance(project);
    List<VirtualFile> roots = JBIterable.of(e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY))
      .filter(o -> o.equals(index.getContentRootForFile(o))).toList();
    String target = AttachDirectoryUtils.getDisplayName(roots);
    String message = IdeBundle.message("detach.directory.dialog.message.detach.0", target);
    if (Messages.showOkCancelDialog(project, message, getConfirmDetachDialogTitle(),
                                    IdeBundle.message("detach.directory.dialog.button.detach"), Messages.getCancelButton(),
                                    Messages.getQuestionIcon()) == Messages.OK) {
      detachDirectoriesWithUndo(project, roots);
    }
  }

  private static @NlsSafe String getTitle(int directoriesAmount) {
    return IdeBundle.message("detach.directory.action.text.detach.0", directoriesAmount);
  }

  private static @NlsSafe String getConfirmDetachDialogTitle() {
    return IdeBundle.message("detach.directory.dialog.title.detach");
  }

  public static void detachDirectoriesWithUndo(@NotNull Project project, @NotNull List<? extends VirtualFile> files) {
    AttachDirectoryUtils.addRemoveEntriesWithUndo(project, null, files, false);
  }
}
