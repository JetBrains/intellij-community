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

import com.intellij.util.containers.IntObjectCache;

public class CachingContentStorage implements IContentStorage {
  public static final int MAX_CACHED_CONTENT_LENGTH = 100 * 1024;

  private final IContentStorage mySubject;
  private final IntObjectCache<byte[]> myCache = new IntObjectCache<byte[]>(50);

  public CachingContentStorage(IContentStorage s) {
    mySubject = s;
  }

  public void save() {
    mySubject.save();
  }

  public void close() {
    mySubject.close();
  }

  public int store(byte[] content) throws BrokenStorageException {
    int id = mySubject.store(content);
    cacheContent(content, id);
    return id;
  }

  public byte[] load(int id) throws BrokenStorageException {
    byte[] result = findInCache(id);
    if (result != null) return result;

    result = mySubject.load(id);
    cacheContent(result, id);
    return result;
  }

  private byte[] findInCache(int id) {
    return myCache.tryKey(id);
  }

  public void remove(int id) {
    mySubject.remove(id);
    myCache.remove(id);
  }

  public void setVersion(int version) {
    mySubject.setVersion(version);
  }

  public int getVersion() {
    return mySubject.getVersion();
  }

  private void cacheContent(byte[] content, int id) {
    if (content.length > MAX_CACHED_CONTENT_LENGTH) return;
    myCache.cacheObject(id, content);
  }
}
