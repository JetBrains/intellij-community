/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.caches;

import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author max
 */
public class FileContent extends UserDataHolderBase {
  private final VirtualFile myVirtualFile;
  private byte[] myCachedBytes;
  private long myCachedLength = -1;
  private long myCachedTimeStamp = -1;
  private Boolean myCachedWritable;

  public FileContent(@NotNull VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  public void cache() throws IOException {
    getBytes();
    getLength();
    getTimeStamp();
    isWritable();
  }

  @NotNull
  public byte[] getBytes() throws IOException {
    if (myCachedBytes == null) {
      myCachedBytes = myVirtualFile.isValid() ? myVirtualFile.contentsToByteArray(false) : ArrayUtil.EMPTY_BYTE_ARRAY;
    }
    return myCachedBytes;
  }

  public void setEmptyContent() {
    myCachedBytes = ArrayUtil.EMPTY_BYTE_ARRAY;
    myCachedLength = 0;
  }

  @NotNull
  public VirtualFile getVirtualFile() {
    return myVirtualFile;
  }

  public long getLength() {
    if (myCachedLength == -1) {
      myCachedLength = myVirtualFile.getLength();
    }
    return myCachedLength;
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