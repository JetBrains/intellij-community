/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

/**
 *
 */
abstract public class VirtualFileAdapter implements VirtualFileListener {
  public void propertyChanged(VirtualFilePropertyEvent event){
  }

  public void contentsChanged(VirtualFileEvent event){
  }

  public void fileCreated(VirtualFileEvent event){
  }

  public void fileDeleted(VirtualFileEvent event){
  }

  public void fileMoved(VirtualFileMoveEvent event){
  }

  public void beforePropertyChange(VirtualFilePropertyEvent event){
  }

  public void beforeContentsChange(VirtualFileEvent event){
  }

  public void beforeFileDeletion(VirtualFileEvent event){
  }

  public void beforeFileMovement(VirtualFileMoveEvent event){
  }
}
