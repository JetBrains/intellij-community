/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;

public abstract class FileEditorManagerAdapter implements FileEditorManagerListener{
  public void fileOpened(FileEditorManager source, VirtualFile file) {}

  public void fileClosed(FileEditorManager source, VirtualFile file) {}

  public void selectionChanged(FileEditorManagerEvent event) {}
}
