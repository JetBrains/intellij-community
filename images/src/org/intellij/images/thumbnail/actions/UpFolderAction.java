// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActionUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Level up to browse images.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class UpFolderAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
    if (view != null) {
      VirtualFile root = view.getRoot();
      if (root != null) {
        VirtualFile parent = root.getParent();
        if (parent != null) {
          view.setRoot(parent);
        }
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    if (ThumbnailViewActionUtil.setEnabled(e)) {
      ThumbnailView view = ThumbnailViewActionUtil.getVisibleThumbnailView(e);
      VirtualFile root = view.getRoot();
      e.getPresentation().setEnabled(root != null && root.getParent() != null && !view.isRecursive());
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
