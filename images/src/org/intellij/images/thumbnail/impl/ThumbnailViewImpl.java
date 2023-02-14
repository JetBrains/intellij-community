// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.intellij.images.ImagesBundle;
import org.intellij.images.ImagesIcons;
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
  private final ThumbnailViewUI myThumbnailViewUI;
  private ThemeFilter myFilter;
  private TagFilter[] myTagFilters;

  ThumbnailViewImpl(Project project) {
    this.project = project;

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    myThumbnailViewUI = new ThumbnailViewUI(this);
    Disposer.register(this, myThumbnailViewUI);
    toolWindow = windowManager.registerToolWindow(TOOLWINDOW_ID, myThumbnailViewUI, ToolWindowAnchor.BOTTOM);
    toolWindow.setStripeTitle(ImagesBundle.message("thumbnails.toolwindow.name"));
    toolWindow.setIcon(ImagesIcons.ThumbnailToolWindow);
    setVisible(false);
  }

  private ThumbnailViewUI getUI() {
    return myThumbnailViewUI;
  }

  @Override
  public void setRoot(@NotNull VirtualFile root) {
    this.root = root;
    updateUI();
  }

  @Override
  public VirtualFile getRoot() {
    return root;
  }

  @Override
  public boolean isRecursive() {
    return recursive;
  }

  @Override
  public void setRecursive(boolean recursive) {
    this.recursive = recursive;
    updateUI();
  }

  @Override
  public void setSelected(@NotNull VirtualFile file, boolean selected) {
    if (isVisible()) {
      getUI().setSelected(file, selected);
    }
  }

  @Override
  public boolean isSelected(@NotNull VirtualFile file) {
    return isVisible() && getUI().isSelected(file);
  }

  @Override
  public VirtualFile @NotNull [] getSelection() {
    if (isVisible()) {
      return getUI().getSelection();
    }
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
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

  @Override
  public boolean isVisible() {
    return toolWindow.isAvailable();
  }

  @Override
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

  @Override
  public TagFilter @Nullable [] getTagFilters() {
    return myTagFilters;
  }

  @Override
  public void setVisible(boolean visible) {
    toolWindow.setAvailable(visible);
    if (visible) {
      setTitle();
      getUI().refresh();
    }
  }

  @Override
  public void refresh() {
    updateUI();
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

  @Override
  @NotNull
  public Project getProject() {
    return project;
  }

  @Override
  public void setTransparencyChessboardVisible(boolean visible) {
    if (isVisible()) {
      getUI().setTransparencyChessboardVisible(visible);
    }
  }

  @Override
  public boolean isTransparencyChessboardVisible() {
    return isVisible() && getUI().isTransparencyChessboardVisible();
  }

  @Override
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

  @Override
  public void dispose() {
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
    @Override
    public void run() {
      SwingUtilities.invokeLater(() -> getUI().scrollToSelection());
    }
  }
}
