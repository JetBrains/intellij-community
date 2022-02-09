// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.contentQueue;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class CachedFileContent {
  private static final Logger LOG = Logger.getInstance(CachedFileContent.class);

  private final VirtualFile myVirtualFile;
  private byte[] myCachedBytes;
  private long myCachedTimeStamp = -1;
  private Boolean myCachedWritable;

  public CachedFileContent(@NotNull VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public byte @NotNull [] getBytes() throws IOException {
    if (myCachedBytes == null) {
      if (myVirtualFile.isValid()) {
        myCachedTimeStamp = myVirtualFile.getTimeStamp();
        myCachedBytes = myVirtualFile.contentsToByteArray(false);
      }
      else {
        myCachedTimeStamp = -1;
        myCachedBytes = ArrayUtilRt.EMPTY_BYTE_ARRAY;
      }
    }
    return myCachedBytes;
  }

  public byte @NotNull [] getBytesOrEmpty() {
    try {
      return getBytes();
    }
    catch (IOException e) {
      if (IndexDebugProperties.DEBUG) {
        LOG.info("Failed to load content for file " + myVirtualFile, e);
      }
      return ArrayUtilRt.EMPTY_BYTE_ARRAY;
    }
  }

  public void setEmptyContent() {
    myCachedBytes = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public long getTimeStamp() {
    if (myCachedTimeStamp == -1) {
      myCachedTimeStamp = myVirtualFile.getTimeStamp();
    }
    return myCachedTimeStamp;
  }

  public boolean isWritable() {
    if (myCachedWritable == null) {
      myCachedWritable = myVirtualFile.isWritable();
    }
    return myCachedWritable == Boolean.TRUE;
  }
}