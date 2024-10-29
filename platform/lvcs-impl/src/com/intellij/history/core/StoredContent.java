// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.history.core;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.io.DataInputOutputUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

@ApiStatus.Internal
public class StoredContent extends Content {
  private static final int UNAVAILABLE = 0;

  private int myContentId;

  public static StoredContent acquireContent(byte[] bytes) {
    return new StoredContent(getFS().storeUnlinkedContent(bytes));
  }

  public static StoredContent acquireContent(VirtualFile f) {
    return new StoredContent(getFS().acquireContent(f));
  }

  public static StoredContent transientContent(VirtualFile f) {
    return new StoredContent(getFS().getCurrentContentId(f)) {
      @Override
      public void release() {
        throw new UnsupportedOperationException();
      }

      @Override
      public void write(DataOutput out) {
        throw new UnsupportedOperationException();
      }
    };
  }

  @TestOnly
  public StoredContent(int contentId) {
    myContentId = contentId;
  }

  public StoredContent(DataInput in) throws IOException {
    myContentId = DataInputOutputUtil.readINT(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    DataInputOutputUtil.writeINT(out, myContentId);
  }

  @Override
  public byte[] getBytes() {
    //todo handle unavailable content
    //if (!isAvailable()) throw new RuntimeException("content is not available");
    try {
      if (myContentId == UNAVAILABLE) return ArrayUtilRt.EMPTY_BYTE_ARRAY;
      return getFS().contentsToByteArray(myContentId);
    }
    catch (IOException e) {
      throw new RuntimeException("cannot get stored content", e);
    }
  }

  @Override
  public boolean isAvailable() {
    //return myContentId != UNAVAILABLE;
    return true;
  }

  private static PersistentFS getFS() {
    return PersistentFS.getInstance();
  }

  public int getContentId() {
    return myContentId;
  }

  @Override
  public void release() {
    if (myContentId == UNAVAILABLE) return;
    getFS().releaseContent(myContentId);
    myContentId = UNAVAILABLE;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof StoredContent content)) return false;
    return myContentId == content.myContentId;
  }

  @Override
  public int hashCode() {
    return myContentId;
  }
}
