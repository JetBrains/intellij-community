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

import com.intellij.util.io.RecordDataOutput;

import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;

public class ContentStorage implements IContentStorage {
  private final com.intellij.util.io.storage.Storage myStore;

  public ContentStorage(File f) throws IOException {
    myStore = com.intellij.util.io.storage.Storage.create(f.getPath());
  }

  public static IContentStorage createContentStorage(File f) throws IOException {
    IContentStorage s = new ContentStorage(f);
    s = new CachingContentStorage(s);
    s = new CompressingContentStorage(s);
    s = new ThreadSafeContentStorage(s);
    return s;
  }

  public void save() {
    // make storage flush all data to prevent
    // its corruption when idea process is killed by force
    myStore.force();
  }

  public void close() {
    myStore.dispose();
  }

  public int store(byte[] content) throws BrokenStorageException {
    try {
      RecordDataOutput r = myStore.createStream();
      r.writeInt(content.length);
      r.write(content);
      r.close();
      return r.getRecordId();
    }
    catch (Throwable e) {
      throw new BrokenStorageException(e);
    }
  }

  public byte[] load(int id) throws BrokenStorageException {
    try {
      DataInputStream r = myStore.readStream(id);
      byte[] buffer = new byte[r.readInt()];
      r.readFully(buffer);
      r.close();
      return buffer;
    }
    catch (Throwable e) {
      throw new BrokenStorageException(e);
    }
  }

  public void remove(int id) {
    myStore.deleteRecord(id);
  }

  public int getVersion() {
    return myStore.getVersion();
  }

  public void setVersion(final int version) {
    myStore.setVersion(version);
  }
}
