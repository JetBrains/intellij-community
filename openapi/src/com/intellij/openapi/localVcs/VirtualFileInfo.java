/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.localVcs;

import java.io.IOException;

/**
 * author: lesya
 */
public interface VirtualFileInfo {
  String getFilePath();
  long getFileTampStamp();
  byte[] getFileByteContent() throws IOException;

  boolean isDirectory();

  String getName();

  int getFileSize();

}
