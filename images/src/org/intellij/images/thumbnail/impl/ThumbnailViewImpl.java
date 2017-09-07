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

/** $Id$ */

package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import icons.ImagesIcons;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.editor.actionSystem.ImageEditorActions;
import org.intellij.images.search.TagFilter;
import org.intellij.images.thumbnail.ThumbnailView;
import org.intellij.images.thumbnail.actions.ThemeFilter;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Thumbnail view.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ThumbnailViewImpl implements ThumbnailView {

  private final Project project;
  private final ToolWindow toolWindow;

  private boolean recursive = false;
  private VirtualFile root = null;
  private final ThumbnailViewUI myThubmnailViewUi;
  private ThemeFilter myFilter;
  private TagFilter[] myTagFilters;

  public ThumbnailViewImpl(Project project) {
    this.project = project;

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    myThubmnailViewUi = new ThumbnailViewUI(this);
    toolWindow = windowManager.registerToolWindow(TOOLWINDOW_ID, myThubmnailViewUi, ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(ImagesIcons.ThumbnailToolWindow);
    setVisible(false);
  }

  private ThumbnailViewUI getUI() {
    return myThubmnailViewUi;
  }

  public void setRoot(@NotNull VirtualFile root) {
    this.root = root;
    updateUI();
  }

  public VirtualFile getRoot() {
    return root;
  }

  public boolean isRecursive() {
    return recursive;
  }

  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
    updateUI();
  }

  public void setSelected(@NotNull VirtualFile file, boolean selected) {
    if (isVisible()) {
      getUI().setSelected(file, selected);
    }
  }

  public boolean isSelected(@NotNull VirtualFile file) {
    return isVisible() && getUI().isSelected(file);
  }

  @NotNull
  public VirtualFile[] getSelection() {
    if (isVisible()) {
      return getUI().getSelection();
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  public void scrollToSelection() {
    if (isVisible()) {
      if (!toolWindow.isActive()) {
        toolWindow.activate(new LazyScroller());
      }
      else {
        getUI().scrollToSelection();
      }
    }
  }

  public boolean isVisible() {
    return toolWindow.isAvailable();
  }

  public void activate() {
    if (isVisible() && !toolWindow.isActive()) {
      toolWindow.activate(null);
    }
  }

  @Override
  public void setFilter(ThemeFilter filter) {
    myFilter = filter;
    updateUI();
  }

  @Override
  public ThemeFilter getFilter() {
    return myFilter;
  }

  @Override
  public void setTagFilters(TagFilter[] filter) {
    myTagFilters = filter;
    updateUI();
  }

  @Nullable
  @Override
  public TagFilter[] getTagFilters() {
    return myTagFilters;
  }

  public void setVisible(boolean visible) {
    toolWindow.setAvailable(visible, null);
    if (visible) {
      setTitle();
      getUI().refresh();
    }
    else {
      getUI().dispose();
    }
  }

  private void updateUI() {
    if (isVisible()) {
      setTitle();
      getUI().refresh();
    }
  }

  private void setTitle() {
    toolWindow.setTitle(root != null ? IfsUtil.getReferencePath(project, root) : null);
  }

  @NotNull
  public Project getProject() {
    return project;
  }

  public void setTransparencyChessboardVisible(boolean visible) {
    if (isVisible()) {
      getUI().setTransparencyChessboardVisible(visible);
    }
  }

  public boolean isTransparencyChessboardVisible() {
    return isVisible() && getUI().isTransparencyChessboardVisible();
  }

  public boolean isEnabledForActionPlace(String place) {
    // Enable if it not for Editor
    return isVisible() && !ImageEditorActions.ACTION_PLACE.equals(place);
  }

  @Override
  public boolean isFileSizeVisible() {
    return isVisible() && getUI().isFileSizeVisible();
  }

  @Override
  public void setFileSizeVisible(boolean visible) {
    if (isVisible()) {
      getUI().setFileSizeVisible(visible);
    }
  }

  @Override
  public boolean isFileNameVisible() {
    return isVisible() && getUI().isFileNameVisible();
  }

  @Override
  public void setFileNameVisible(boolean visible) {
    if (isVisible()) {
      getUI().setFileNameVisible(visible);
    }
  }

  public void dispose() {
    // Dispose UI
    Disposer.dispose(getUI());
    // Unregister ToolWindow
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    windowManager.unregisterToolWindow(TOOLWINDOW_ID);
  }

  @Override
  public ImageZoomModel getZoomModel() {
    return ImageZoomModel.STUB;
  }

  @Override
  public void setGridVisible(boolean visible) {
  }

  @Override
  public boolean isGridVisible() {
    return false;
  }

  private final class LazyScroller implements Runnable {
    public void run() {
      SwingUtilities.invokeLater(() -> getUI().scrollToSelection());
    }
  }
}
