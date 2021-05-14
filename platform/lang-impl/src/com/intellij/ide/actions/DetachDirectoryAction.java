// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
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
public class DetachDirectoryAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    ProjectFileIndex index = project == null ? null : ProjectFileIndex.SERVICE.getInstance(project);
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
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    ProjectFileIndex index = ProjectFileIndex.SERVICE.getInstance(project);
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

  @NlsSafe
  protected String getTitle(int directoriesAmount) {
    return IdeBundle.message("detach.directory.action.text.detach.0", directoriesAmount);
  }

  @NlsSafe
  protected String getConfirmDetachDialogTitle() {
    return IdeBundle.message("detach.directory.dialog.title.detach");
  }

  public static void detachDirectoriesWithUndo(@NotNull Project project, @NotNull List<VirtualFile> files) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    if (modules.length == 0) return;
    AttachDirectoryUtils.addRemoveEntriesWithUndo(modules[0], files, false);
  }
}
