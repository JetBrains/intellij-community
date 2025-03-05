// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.images.thumbnail.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.ImagesBundle;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

final class ThumbnailSelectInTarget implements SelectInTarget, DumbAware {
  ThumbnailSelectInTarget() {
  }

  @Override
  public boolean canSelect(SelectInContext context) {
    VirtualFile virtualFile = context.getVirtualFile();
    return ImageFileTypeManager.getInstance().isImage(virtualFile) && virtualFile.getParent() != null;
  }

  @Override
  public void selectIn(SelectInContext context, final boolean requestFocus) {
    VirtualFile virtualFile = context.getVirtualFile();
    VirtualFile parent = virtualFile.getParent();
    if (parent != null) {
      final Project project = context.getProject();
      ThumbnailView thumbnailView = ThumbnailManager.getManager(project).getThumbnailView();
      thumbnailView.setRoot(parent);
      thumbnailView.setVisible(true);
      thumbnailView.setSelected(virtualFile);
      thumbnailView.scrollToSelection();
    }
  }

  @Override
  public String toString() {
    return ImagesBundle.message("thumbnails.toolwindow.name");
  }

  @Override
  public String getToolWindowId() {
    return ThumbnailView.TOOLWINDOW_ID;
  }

  @Override
  public String getMinorViewId() {
    return null;
  }

  @Override
  public float getWeight() {
    return 10;
  }
}
