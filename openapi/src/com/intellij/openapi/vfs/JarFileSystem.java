/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.vfs;

import com.intellij.openapi.application.ApplicationManager;

import java.io.IOException;
import java.util.zip.ZipFile;

public abstract class JarFileSystem extends VirtualFileSystem {
  public static final String PROTOCOL = "jar";
  public static final String JAR_SEPARATOR = "!/";

  public static JarFileSystem getInstance(){
    return ApplicationManager.getApplication().getComponent(JarFileSystem.class);
  }

  public abstract VirtualFile getVirtualFileForJar(VirtualFile entryVFile);
  public abstract ZipFile getJarFile(VirtualFile entryVFile) throws IOException;
}