// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.editor.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.fileTypes.impl.ImageFileType;
import org.jetbrains.annotations.NotNull;

public class SetBackgroundImageAction extends DumbAwareAction {

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);

    boolean image = file != null && FileTypeRegistry.getInstance().isFileOfType(file, ImageFileType.INSTANCE); // do not show for SVG
    boolean visible = !e.isFromContextMenu() || image;
    e.getPresentation().setEnabled(project != null);
    e.getPresentation().setVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
    boolean image = file != null && ImageFileTypeManager.getInstance().isImage(file);

    BackgroundImageDialog dialog = new BackgroundImageDialog(project, image ? file.getPath() : null);
    dialog.showAndGet();
  }
}
