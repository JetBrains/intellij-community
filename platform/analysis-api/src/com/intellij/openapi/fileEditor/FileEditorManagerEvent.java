// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;

public final class FileEditorManagerEvent extends EventObject {

  private final VirtualFile        myOldFile;
  private final FileEditor         myOldEditor;
  private final VirtualFile        myNewFile;
  private final FileEditor         myNewEditor;
  private final FileEditorProvider myOldProvider;
  private final FileEditorProvider myNewProvider;

  public FileEditorManagerEvent(@NotNull FileEditorManager source,
                                @Nullable VirtualFile oldFile,
                                @Nullable FileEditor oldEditor,
                                @Nullable VirtualFile newFile,
                                @Nullable FileEditor newEditor)
  {
    this(source, oldFile, oldEditor, null, newFile, newEditor, null);
  }
  
  public FileEditorManagerEvent(@NotNull FileEditorManager source,
                                @Nullable VirtualFile oldFile,
                                @Nullable FileEditor oldEditor,
                                @Nullable FileEditorProvider oldProvider,
                                @Nullable VirtualFile newFile,
                                @Nullable FileEditor newEditor,
                                @Nullable FileEditorProvider newProvider)
  {
    super(source);
    myOldFile = oldFile;
    myOldEditor = oldEditor;
    myNewFile = newFile;
    myNewEditor = newEditor;
    myOldProvider = oldProvider;
    myNewProvider = newProvider;
  }

  @NotNull
  public FileEditorManager getManager(){
    return (FileEditorManager)getSource();
  }

  @Nullable
  public VirtualFile getOldFile() {
    return myOldFile;
  }

  @Nullable
  public VirtualFile getNewFile() {
    return myNewFile;
  }

  @Nullable
  public FileEditor getOldEditor() {
    return myOldEditor;
  }

  @Nullable
  public FileEditor getNewEditor() {
    return myNewEditor;
  }

  @Nullable
  public FileEditorProvider getOldProvider() {
    return myOldProvider;
  }

  @Nullable
  public FileEditorProvider getNewProvider() {
    return myNewProvider;
  }
}
