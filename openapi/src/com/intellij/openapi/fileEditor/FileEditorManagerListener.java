/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.fileEditor;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.EventListener;

public interface FileEditorManagerListener extends EventListener{
  /**
   * TODO[vova] write javadoc
   */
  void fileOpened(FileEditorManager source, VirtualFile file);
  
  /**
   * TODO[vova] write javadoc
   */
  void fileClosed(FileEditorManager source, VirtualFile file);

  /**
   * TODO[vova] write javadoc
   */
  void selectionChanged(FileEditorManagerEvent event);
}