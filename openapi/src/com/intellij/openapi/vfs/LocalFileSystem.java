/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.File;

public abstract class LocalFileSystem extends VirtualFileSystem {
  public static final String PROTOCOL = "file";

  public static LocalFileSystem getInstance(){

    return ApplicationManager.getApplication().getComponent(LocalFileSystem.class);
  }

  public abstract VirtualFile findFileByIoFile(File file);
  public abstract VirtualFile refreshAndFindFileByIoFile(File file);
}