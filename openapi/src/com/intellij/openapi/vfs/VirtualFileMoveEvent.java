/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

/**
 *
 */
public class VirtualFileMoveEvent extends VirtualFileEvent {
  private final VirtualFile myOldParent;
  private final VirtualFile myNewParent;

  public VirtualFileMoveEvent(Object requestor, VirtualFile file, VirtualFile oldParent, VirtualFile newParent){
    super(requestor, file, file.getName(), file.isDirectory(), file.getParent());
    myOldParent = oldParent;
    myNewParent = newParent;
  }

  public VirtualFile getOldParent(){
    return myOldParent;
  }

  public VirtualFile getNewParent(){
    return myNewParent;
  }
}
