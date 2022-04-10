// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public final class FileEditorManagerEvent extends EventObject {
  private final VirtualFile myOldFile;
  private final FileEditor myOldEditor;
  private final FileEditorProvider myOldProvider;
  private final VirtualFile myNewFile;
  private final FileEditor myNewEditor;
  private final FileEditorProvider myNewProvider;

  public FileEditorManagerEvent(@NotNull FileEditorManager source,
                                @Nullable FileEditorWithProvider oldEditorWithProvider,
                                @Nullable FileEditorWithProvider newEditorWithProvider) {
    this(source, oldEditorWithProvider != null ? oldEditorWithProvider.getFileEditor().getFile() : null,
         oldEditorWithProvider != null ? oldEditorWithProvider.getFileEditor() : null,
         oldEditorWithProvider != null ? oldEditorWithProvider.getProvider() : null,
         newEditorWithProvider != null ? newEditorWithProvider.getFileEditor().getFile() : null,
         newEditorWithProvider != null ? newEditorWithProvider.getFileEditor() : null,
         newEditorWithProvider != null ? newEditorWithProvider.getProvider() : null);
  }

  /**
   * @deprecated Use constructor accepting {@link FileEditorWithProvider}
   */
  @Deprecated
  public FileEditorManagerEvent(@NotNull FileEditorManager source,
                                @Nullable VirtualFile oldFile,
                                @Nullable FileEditor oldEditor,
                                @Nullable VirtualFile newFile,
                                @Nullable FileEditor newEditor) {
    this(source, oldFile, oldEditor, null, newFile, newEditor, null);
  }

  /**
   * @deprecated Use constructor accepting {@link FileEditorWithProvider}
   */
  @Deprecated
  public FileEditorManagerEvent(@NotNull FileEditorManager source,
                                @Nullable VirtualFile oldFile,
                                @Nullable FileEditor oldEditor,
                                @Nullable FileEditorProvider oldProvider,
                                @Nullable VirtualFile newFile,
                                @Nullable FileEditor newEditor,
                                @Nullable FileEditorProvider newProvider) {
    super(source);
    myOldFile = oldFile;
    myOldEditor = oldEditor;
    myNewFile = newFile;
    myNewEditor = newEditor;
    myOldProvider = oldProvider;
    myNewProvider = newProvider;
  }

  @NotNull
  public FileEditorManager getManager() {
    return (FileEditorManager)getSource();
  }

  public VirtualFile getOldFile() {
    return myOldFile;
  }

  public VirtualFile getNewFile() {
    return myNewFile;
  }

  public FileEditor getOldEditor() {
    return myOldEditor;
  }

  public FileEditor getNewEditor() {
    return myNewEditor;
  }

  public FileEditorProvider getOldProvider() {
    return myOldProvider;
  }

  public FileEditorProvider getNewProvider() {
    return myNewProvider;
  }
}
