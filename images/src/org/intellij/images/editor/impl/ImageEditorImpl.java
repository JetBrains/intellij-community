/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.editor.impl;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import org.intellij.images.editor.ImageDocument;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.actionSystem.ThumbnailViewActions;
import org.intellij.images.vfs.IfsUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * Image viewer implementation.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ImageEditorImpl implements ImageEditor {
  private final Project project;
  private final VirtualFile file;
  private final ImageEditorUI editorUI;
  private boolean disposed;

  public ImageEditorImpl(@NotNull Project project, @NotNull VirtualFile file) {
    this(project, file, false, false);
  }

    /**
     * @param isEmbedded if it's true the toolbar and the image info are disabled and an image is left-side aligned
     * @param isOpaque if it's false, all components of the editor are transparent
     */
  public ImageEditorImpl(@NotNull Project project, @NotNull VirtualFile file, boolean isEmbedded, boolean isOpaque) {
    this.project = project;
    this.file = file;

    editorUI = new ImageEditorUI(this, isEmbedded, isOpaque);
    Disposer.register(this, editorUI);

    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
        ImageEditorImpl.this.propertyChanged(event);
      }

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        ImageEditorImpl.this.contentsChanged(event);
      }
    }, this);

    setValue(file);
  }

  void setValue(VirtualFile file) {
    try {
      editorUI.setImageProvider(IfsUtil.getImageProvider(file), IfsUtil.getFormat(file));
    }
    catch (Exception e) {
      //     Error loading image file
      editorUI.setImageProvider(null, null);
    }
  }

  @Override
  public boolean isValid() {
    ImageDocument document = editorUI.getImageComponent().getDocument();
    return document.getValue() != null;
  }

  @Override
  public ImageEditorUI getComponent() {
    return editorUI;
  }

  @Override
  public JComponent getContentComponent() {
    return editorUI.getImageComponent();
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return file;
  }

  @Override
  @NotNull
  public Project getProject() {
    return project;
  }

  @Override
  public ImageDocument getDocument() {
    return editorUI.getImageComponent().getDocument();
  }

  @Override
  public void setTransparencyChessboardVisible(boolean visible) {
    editorUI.getImageComponent().setTransparencyChessboardVisible(visible);
    editorUI.repaint();
  }

  @Override
  public boolean isTransparencyChessboardVisible() {
    return editorUI.getImageComponent().isTransparencyChessboardVisible();
  }

  @Override
  public boolean isEnabledForActionPlace(String place) {
    // Disable for thumbnails action
    return !ThumbnailViewActions.ACTION_PLACE.equals(place);
  }

  @Override
  public void setGridVisible(boolean visible) {
    editorUI.getImageComponent().setGridVisible(visible);
    editorUI.repaint();
  }

  @Override
  public void setEditorBackground(Color color) {
    editorUI.getImageComponent().getParent().setBackground(color);
  }

  @Override
  public void setBorderVisible(boolean visible) {
    editorUI.getImageComponent().setBorderVisible(visible);
  }

  @Override
  public boolean isGridVisible() {
    return editorUI.getImageComponent().isGridVisible();
  }

  @Override
  public boolean isDisposed() {
    return disposed;
  }

  @Override
  public ImageZoomModel getZoomModel() {
    return editorUI.getZoomModel();
  }

  @Override
  public void dispose() {
    disposed = true;
  }

  void propertyChanged(@NotNull VirtualFilePropertyEvent event) {
    if (file.equals(event.getFile())) {
      // Change document
      file.refresh(true, false, () -> {
        if (ImageFileTypeManager.getInstance().isImage(file)) {
          setValue(file);
        }
        else {
          setValue(null);
          // Close editor
          FileEditorManager editorManager = FileEditorManager.getInstance(project);
          editorManager.closeFile(file);
        }
      });
    }
  }

  void contentsChanged(@NotNull VirtualFileEvent event) {
    if (file.equals(event.getFile())) {
      // Change document
      refreshFile();
    }
  }

  public void refreshFile() {
    Runnable postRunnable = () -> setValue(file);
    RefreshQueue.getInstance().refresh(true, false, postRunnable, ModalityState.current(), file);
  }
}
