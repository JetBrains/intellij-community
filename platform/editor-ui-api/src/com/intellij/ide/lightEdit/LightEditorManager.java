// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

@ApiStatus.Experimental
public interface LightEditorManager {
  void addListener(@NotNull LightEditorListener listener);

  void addListener(@NotNull LightEditorListener listener, @NotNull Disposable disposable);

  LightEditorInfo saveAs(@NotNull LightEditorInfo info, @NotNull VirtualFile targetFile);

  @NotNull LightEditorInfo createEmptyEditor(@Nullable String preferredName);

  @Nullable LightEditorInfo createEditor(@NotNull VirtualFile file);

  void closeEditor(@NotNull LightEditorInfo editorInfo);

  boolean containsUnsavedDocuments();

  boolean isImplicitSaveAllowed(@NotNull Document document);

  @NotNull
  @Unmodifiable
  Collection<VirtualFile> getOpenFiles();

  @NotNull
  @Unmodifiable
  Collection<LightEditorInfo> getEditors(@NotNull VirtualFile virtualFile);

  boolean isFileOpen(@NotNull VirtualFile file);
}
