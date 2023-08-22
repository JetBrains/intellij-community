// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.lightEdit;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public final class LightEditorInfoImpl implements LightEditorInfo {
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
  public @NotNull VirtualFile getFile() {
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

  public static @Nullable Editor getEditor(@Nullable LightEditorInfo editorInfo) {
    return getEditor(editorInfo != null ? editorInfo.getFileEditor() : null);
  }

  public static @Nullable Editor getEditor(@Nullable FileEditor fileEditor) {
    TextEditor textEditor = fileEditor instanceof TextEditor ? (TextEditor)fileEditor : null;
    return textEditor == null ? null : textEditor.getEditor();
  }

  public @NotNull FileEditorProvider getProvider() {
    return myProvider;
  }

  @Override
  public boolean isSaveRequired() {
    return isUnsaved() &&
           (!isNew() || myFileEditor instanceof TextEditor && ((TextEditor)myFileEditor).getEditor().getDocument().getTextLength() > 0);
  }

  @Override
  public @Nullable Path getPreferredSavePath() {
    return myPreferredSavePath;
  }

  @Override
  public void setPreferredSavePath(@Nullable Path preferredSavePath) {
    myPreferredSavePath = preferredSavePath;
  }
}
