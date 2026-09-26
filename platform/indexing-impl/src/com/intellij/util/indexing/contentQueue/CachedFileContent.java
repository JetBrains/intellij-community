// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.indexing.impl.IndexDebugProperties;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.NoSuchFileException;

@Internal
public final class CachedFileContent {
  private static final Logger LOG = Logger.getInstance(CachedFileContent.class);

  private final VirtualFile myVirtualFile;
  private byte[] myCachedBytes;
  private CharSequence myText;
  private long myCachedTimeStamp = -1;
  private Boolean myCachedWritable;

  public CachedFileContent(@NotNull VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public byte @NotNull [] getBytes() throws IOException {
    if (myCachedBytes == null) {
      if (myVirtualFile.isValid()) {
        try {
          myCachedTimeStamp = myVirtualFile.getTimeStamp();
          myCachedBytes = myVirtualFile.contentsToByteArray(/*cacheContent: */false);
        }
        catch (NoSuchFileException e) {
          //We can't fully avoid scenarios there e.g. a third party (git) removes the file that is already queued for
          // indexing -- and VFS haven't captured it's new state yet.
          // So it is not an error, and we must be ready for this scenario:
          LOG.info("File [" + myVirtualFile + "] is deleted while indexing is running", e);
          myCachedTimeStamp = -1;
          myCachedBytes = ArrayUtilRt.EMPTY_BYTE_ARRAY;
        }
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

  public CharSequence getText() throws IOException {
    if (myText == null) {
      myText = LoadTextUtil.getTextByBinaryPresentation(getBytes(), myVirtualFile, false, false);
    }
    return myText;
  }

  @NotNull
  public CharSequence getTextOrEmpty() {
    try {
      return getText();
    }
    catch (IOException e) {
      if (IndexDebugProperties.DEBUG) {
        LOG.info("Failed to decode text for file " + myVirtualFile, e);
      }
      return "";
    }
  }

  public void setEmptyContent() {
    myCachedBytes = ArrayUtilRt.EMPTY_BYTE_ARRAY;
  }

  public @NotNull VirtualFile getVirtualFile() {
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
    return myCachedWritable;
  }
}