// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Experimental
public interface LightEditService {

  static LightEditService getInstance() {
    return ServiceManager.getService(LightEditService.class);
  }

  void createNewFile();

  void saveToAnotherFile(@NotNull Editor editor);

  Project getProject();

  Project getOrCreateProject();

  void openFile(@NotNull VirtualFile file);

  boolean isAutosaveMode();

  void setAutosaveMode(boolean autosaveMode);

  boolean closeEditorWindow();

  LightEditorManager getEditorManager();

  @Nullable
  VirtualFile getSelectedFile();
}
