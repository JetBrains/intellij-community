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

import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Image editor provider.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ImageFileEditorProvider implements FileEditorProvider, DumbAware {
  @NonNls private static final String EDITOR_TYPE_ID = "images";

  private final ImageFileTypeManager typeManager;

  ImageFileEditorProvider(ImageFileTypeManager typeManager) {
    this.typeManager = typeManager;
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return typeManager.isImage(file);
  }

  @Override
  @NotNull
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    ImageFileEditorImpl viewer = new ImageFileEditorImpl(project, file);
    if ("svg".equalsIgnoreCase(file.getExtension())) {
      return new TextEditorWithPreview((TextEditor)TextEditorProvider.getInstance().createEditor(project, file), viewer, "SvgEditor");
    }
    return viewer;
  }

  @Override
  @NotNull
  public String getEditorTypeId() {
    return EDITOR_TYPE_ID;
  }

  @Override
  @NotNull
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.HIDE_DEFAULT_EDITOR;
  }
}
