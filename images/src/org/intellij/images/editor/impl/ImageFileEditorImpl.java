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

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.EventDispatcher;
import org.intellij.images.editor.ImageEditor;
import org.intellij.images.editor.ImageFileEditor;
import org.intellij.images.editor.ImageZoomModel;
import org.intellij.images.options.*;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * Image Editor.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorImpl extends UserDataHolderBase implements ImageFileEditor, PropertyChangeListener {
  private static final String NAME = "ImageFileEditor";

  private final ImageEditor imageEditor;
  private final EventDispatcher<PropertyChangeListener> myDispatcher = EventDispatcher.create(PropertyChangeListener.class);

  ImageFileEditorImpl(@NotNull Project project, @NotNull VirtualFile file) {
    imageEditor = new ImageEditorImpl(project, file);
    Disposer.register(this, imageEditor);

    // Set background and grid default options
    Options options = OptionsManager.getInstance().getOptions();
    EditorOptions editorOptions = options.getEditorOptions();
    GridOptions gridOptions = editorOptions.getGridOptions();
    TransparencyChessboardOptions transparencyChessboardOptions = editorOptions.getTransparencyChessboardOptions();
    imageEditor.setGridVisible(gridOptions.isShowDefault());
    imageEditor.setTransparencyChessboardVisible(transparencyChessboardOptions.isShowDefault());

    ((ImageEditorImpl)imageEditor).getComponent().getImageComponent().addPropertyChangeListener(this);
  }

  @NotNull
  public JComponent getComponent() {
    return imageEditor.getComponent();
  }

  public JComponent getPreferredFocusedComponent() {
    return imageEditor.getContentComponent();
  }

  @NotNull
  public String getName() {
    return NAME;
  }

  @NotNull
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    ImageZoomModel zoomModel = imageEditor.getZoomModel();
    return new ImageFileEditorState(
      imageEditor.isTransparencyChessboardVisible(),
      imageEditor.isGridVisible(),
      zoomModel.getZoomFactor());
  }

  public void setState(@NotNull FileEditorState state) {
    if (state instanceof ImageFileEditorState) {
      ImageFileEditorState editorState = (ImageFileEditorState)state;
      ImageZoomModel zoomModel = imageEditor.getZoomModel();
      imageEditor.setTransparencyChessboardVisible(editorState.isBackgroundVisible());
      imageEditor.setGridVisible(editorState.isGridVisible());
      zoomModel.setZoomFactor(editorState.getZoomFactor());
    }
  }

  public boolean isModified() {
    return false;
  }

  public boolean isValid() {
    return true;
  }

  public void selectNotify() {
  }

  public void deselectNotify() {
  }

  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDispatcher.addListener(listener);
  }

  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public void propertyChange(@NotNull PropertyChangeEvent event) {
    PropertyChangeEvent editorEvent = new PropertyChangeEvent(this, event.getPropertyName(), event.getOldValue(), event.getNewValue());
    myDispatcher.getMulticaster().propertyChange(editorEvent);
  }

  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }

  public void dispose() {
  }

  @NotNull
  public ImageEditor getImageEditor() {
    return imageEditor;
  }
}
