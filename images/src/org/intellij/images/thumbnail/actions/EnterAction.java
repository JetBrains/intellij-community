// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Level up to browse images.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class EnterAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    if (view != null) {
      VirtualFile[] selection = view.getSelection();
      if (selection.length == 1 && selection[0].isDirectory()) {
        view.setRoot(selection[0]);
      }
      else if (selection.length > 0) {
        FileEditorManager fileEditorManager = FileEditorManager.getInstance(view.getProject());
        ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
        for (VirtualFile file : selection) {
          if (typeManager.isImage(file)) {
            fileEditorManager.openFile(file, false);
          }
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (ThumbnailViewActionUtil.setEnabled(e)) {
      Presentation presentation = e.getPresentation();
      ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
      VirtualFile[] selection = view.getSelection();
      if (selection.length > 0) {
        if (selection.length == 1 && selection[0].isDirectory()) {
          presentation.setVisible(true);
        }
        else {
          boolean notImages = false;
          ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
          for (VirtualFile file : selection) {
            notImages |= !typeManager.isImage(file);
          }
          presentation.setEnabled(!notImages);
          presentation.setVisible(false);
        }
      }
      else {
        presentation.setEnabledAndVisible(false);
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
