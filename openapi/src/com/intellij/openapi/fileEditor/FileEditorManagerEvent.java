/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventObject;

public final class FileEditorManagerEvent extends EventObject {
  private final VirtualFile myOldFile;
  private final FileEditor myOldEditor;
  private final VirtualFile myNewFile;
  private final FileEditor myNewEditor;

  public FileEditorManagerEvent(
    FileEditorManager source,
    VirtualFile oldFile,
    FileEditor oldEditor,
    VirtualFile newFile,
    FileEditor newEditor
  ){
    super(source);
    myOldFile = oldFile;
    myOldEditor = oldEditor;
    myNewFile = newFile;
    myNewEditor = newEditor;
  }

  public FileEditorManager getManager(){
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
}