/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs.pointers;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;

public abstract class VirtualFilePointerManager {
  public static VirtualFilePointerManager getInstance() {
    return ApplicationManager.getApplication().getComponent(VirtualFilePointerManager.class);
  }

  public abstract VirtualFilePointer create(String url, VirtualFilePointerListener listener);

  public abstract VirtualFilePointer create(VirtualFile file, VirtualFilePointerListener listener);

  public abstract VirtualFilePointer duplicate (VirtualFilePointer pointer, VirtualFilePointerListener listener);

  public abstract void kill(VirtualFilePointer pointer);

  public abstract VirtualFilePointerContainer createContainer();

  public abstract VirtualFilePointerContainer createContainer(VirtualFilePointerListener listener);

  public abstract VirtualFilePointerContainer createContainer(VirtualFilePointerFactory factory);
}
