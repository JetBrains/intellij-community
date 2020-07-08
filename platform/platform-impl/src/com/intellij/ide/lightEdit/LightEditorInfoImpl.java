// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public class LightEditorInfoImpl implements LightEditorInfo {

  private final FileEditorProvider myProvider;
  private final FileEditor myFileEditor;
  private final VirtualFile myFile;

  private @Nullable Path myPreferredSavePath;

  LightEditorInfoImpl(@NotNull FileEditorProvider provider, @NotNull FileEditor fileEditor, @NotNull VirtualFile file) {
    myProvider = provider;
    myFileEditor = fileEditor;
    myFile = file;
  }

  @Override
  public @NotNull FileEditor getFileEditor() {
    return myFileEditor;
  }

  @Override
  @NotNull
  public VirtualFile getFile() {
    return myFile;
  }

  @Override
  public boolean isUnsaved() {
    if (isNew()) {
      return true;
    }
    else {
      return FileDocumentManager.getInstance().isFileModified(myFile);
    }
  }

  @Override
  public boolean isNew() {
    return myFile instanceof LightVirtualFile;
  }

  public void disposeEditor() {
    myProvider.disposeEditor(myFileEditor);
  }

  @Nullable
  public static Editor getEditor(@Nullable LightEditorInfo editorInfo) {
    return getEditor(editorInfo != null ? editorInfo.getFileEditor() : null);
  }

  @Nullable
  public static Editor getEditor(@Nullable FileEditor fileEditor) {
    TextEditor textEditor = ObjectUtils.tryCast(fileEditor, TextEditor.class);
    return textEditor != null ? textEditor.getEditor() : null;
  }

  @NotNull
  public FileEditorProvider getProvider() {
    return myProvider;
  }

  @Nullable
  @Override
  public Path getPreferredSavePath() {
    return myPreferredSavePath;
  }

  @Override
  public void setPreferredSavePath(@Nullable Path preferredSavePath) {
    myPreferredSavePath = preferredSavePath;
  }
}
