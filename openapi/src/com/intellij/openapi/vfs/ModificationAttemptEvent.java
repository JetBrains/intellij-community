/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import java.util.EventObject;

public class ModificationAttemptEvent extends EventObject{
  private final VirtualFile[] myFiles;
  private boolean myConsumed = false;

  public ModificationAttemptEvent(VirtualFileManager manager, VirtualFile[] files) {
    super(manager);
    myFiles = files;
  }

  public VirtualFile[] getFiles() {
    return myFiles;
  }

  public void consume(){
    myConsumed = true;
  }

  public boolean isConsumed() {
    return myConsumed;
  }
}
