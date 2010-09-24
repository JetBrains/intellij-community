/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.core;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.TestOnly;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

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
      public void write(DataOutput out) throws IOException {
        throw new UnsupportedOperationException();
      }
    };
  }

  @TestOnly
  public StoredContent(int contentId) {
    myContentId = contentId;
  }

  public StoredContent(DataInput in) throws IOException {
    myContentId = in.readInt();
  }

  @Override
  public void write(DataOutput out) throws IOException {
    out.writeInt(myContentId);
  }

  @Override
  public byte[] getBytes() {
    //todo handle unavailable content 
    //if (!isAvailable()) throw new RuntimeException("content is not available");
    try {
      if (myContentId == UNAVAILABLE) return ArrayUtil.EMPTY_BYTE_ARRAY;
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
    return ((PersistentFS)PersistentFS.getInstance());
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
    return myContentId == ((StoredContent)o).myContentId;
  }

  @Override
  public int hashCode() {
    return myContentId;
  }
}
