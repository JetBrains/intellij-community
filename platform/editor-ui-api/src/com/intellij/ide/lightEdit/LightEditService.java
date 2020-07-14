// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;

@ApiStatus.Experimental
public interface LightEditService {
  @Topic.AppLevel Topic<LightEditServiceListener> TOPIC = new Topic<>(LightEditServiceListener.class);

  String WINDOW_NAME = "LightEdit";

  static LightEditService getInstance() {
    return ServiceManager.getService(LightEditService.class);
  }

  /**
   * Creates an empty document with the specified {@code preferredSavePath} and opens an editor tab.
   *
   * @param preferredSavePath The preferred path to save the document by default. The path must contain at least a file name. If the path
   *                          is valid, it will be used to save the document without a file save dialog. If {@code preferredSavePath} is
   *                          {@code null}, the new document will have a default name "untitled_...".
   * @return An editor info for the newly created document.
   */
  LightEditorInfo createNewDocument(@Nullable Path preferredSavePath);

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

  /**
   * Prompt a user to save all new documents which haven't been written to files yet.
   */
  void saveNewDocuments();
}
