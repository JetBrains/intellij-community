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

package com.intellij.history.core.storage;

import java.io.IOException;

public class StoredContent extends Content {
  private final Storage myStorage;
  private final int myId;

  public StoredContent(Storage s, int id) {
    myStorage = s;
    myId = id;
  }

  public StoredContent(Stream s) throws IOException {
    myId = s.readInteger();
    myStorage = s.getStorage();
  }

  @Override
  public void write(Stream s) throws IOException {
    s.writeInteger(myId);
  }

  @Override
  public byte[] getBytes() {
    try {
      return getBytesUnsafe();
    }
    catch (BrokenStorageException e) {
      throw new RuntimeException(e);
    }
  }

  private byte[] getBytesUnsafe() throws BrokenStorageException {
    return myStorage.loadContentData(myId);
  }

  @Override
  public boolean isAvailable() {
    try {
      getBytesUnsafe();
      return true;
    }
    catch (BrokenStorageException e) {
      return false;
    }
  }

  public int getId() {
    return myId;
  }

  @Override
  public void purge() {
    myStorage.purgeContent(this);
  }

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && myId == ((StoredContent)o).myId;
  }

  @Override
  public int hashCode() {
    return myId;
  }
}
