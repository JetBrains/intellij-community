/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import com.intellij.openapi.vfs.VirtualFile;


/**
 * @author Mike
 */
public interface LvcsFile extends LvcsObject {
  byte[] getByteContent();
  byte[] getByteContent(LvcsLabel label);

  void commit(VirtualFileInfo fileInfo);
  void commitRefactorings(byte[] newContent);

  long getByteLength(LvcsLabel label);

  long getTimeStamp();
}
