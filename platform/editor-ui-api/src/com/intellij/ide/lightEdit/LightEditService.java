// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@ApiStatus.Experimental
public interface LightEditService {

  static LightEditService getInstance() {
    return ServiceManager.getService(LightEditService.class);
  }

  LightEditorInfo createNewFile(@Nullable String preferredName);

  void saveToAnotherFile(@NotNull VirtualFile file);

  void showEditorWindow();

  Project getProject();

  @NotNull Project getOrCreateProject();

  boolean openFile(@NotNull VirtualFile file);

  boolean isAutosaveMode();

  void setAutosaveMode(boolean autosaveMode);

  boolean closeEditorWindow();

  @NotNull
  LightEditorManager getEditorManager();

  @Nullable
  VirtualFile getSelectedFile();

  @Nullable
  FileEditor getSelectedFileEditor();

  void updateFileStatus(@NotNull Collection<VirtualFile> files);
}
