/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Mike
 */
public interface LvcsDirectory extends LvcsObject {
  LvcsFile[] getFiles();
  LvcsFile[] getFiles(LvcsLabel label);

  LvcsDirectory[] getDirectories();
  LvcsDirectory[] getDirectories(LvcsLabel label);

  LvcsObject[] getChildren();
  LvcsObject[] getChildren(LvcsLabel label);

  LvcsDirectory addDirectory(String name, VirtualFile onDisk);

  LvcsFile addFile(String name, VirtualFileInfo virtualFileInfo);
}
