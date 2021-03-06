/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.intellij.images.thumbnail.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.ImagesBundle;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

final class ThumbnailSelectInTarget implements SelectInTarget {
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
      thumbnailView.setSelected(virtualFile, true);
      thumbnailView.scrollToSelection();
    }
  }

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
