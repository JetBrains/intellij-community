/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.make;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.SLRUMap;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.KeyDescriptor;
import com.intellij.util.io.PersistentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 1, 2008
 */
public class CachedPersistentHashMap<Key, Value> extends PersistentHashMap<Key, Value> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.compiler.make.CachedPersistentHashMap");
  protected final SLRUMap<Key, Value> myCache;

  public CachedPersistentHashMap(File file, KeyDescriptor<Key> keyDescriptor, DataExternalizer<Value> valDescriptor, final int cacheSize) throws IOException {
    super(file, keyDescriptor, valDescriptor);
    myCache = new SLRUMap<Key,Value>(cacheSize * 2, cacheSize) {
      protected void onDropFromCache(Key key, Value value) {
        if (isValueDirty(value)) {
          try {
            CachedPersistentHashMap.super.put(key, value);
          }
          catch (IOException e) {
            LOG.info(e);
          }
        }
      }
    };
  }

  protected boolean isValueDirty(Value value) {
    return false;
  }

  @Override
  protected void doPut(Key key, Value value) throws IOException {
    myCache.remove(key);
    super.doPut(key, value);
  }

  @Override
  protected void doAppendData(Key key, @NotNull ValueDataAppender appender) throws IOException {
    myCache.remove(key);
    super.doAppendData(key, appender);
  }

  @Nullable
  protected Value doGet(Key key) throws IOException {
    Value value = myCache.get(key);
    if (value == null) {
      value = super.doGet(key);
      if (value != null) {
        myCache.put(key, value);
      }
    }
    return value;
  }

  @Override
  protected boolean doContainsMapping(Key key) throws IOException {
    final Value value = myCache.get(key);
    return value != null || super.doContainsMapping(key);
  }

  @Override
  protected void doRemove(Key key) throws IOException {
    myCache.remove(key);
    super.doRemove(key);
  }

  @Override
  protected void doForce() {
    try {
      clearCache();
    }
    finally {
      super.doForce();
    }
  }

  @Override
  protected void doClose() throws IOException {
    try {
      clearCache();
    }
    finally {
      super.doClose();
    }
  }

  private void clearCache() {
    myCache.clear();
  }
}
