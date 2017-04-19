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
package com.jetbrains.jsonSchema.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.BidirectionalMap;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

import static com.jetbrains.jsonSchema.impl.JsonSchemaReader.LOG;

/**
 * todo to be removed
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaExportedDefinitions {
  private final Object myLock;
  private final BidirectionalMap<String, VirtualFile> myId2Key;
  private Set<VirtualFile> myFilesToRefresh;

  public JsonSchemaExportedDefinitions() {
    myLock = new Object();
    myId2Key = new BidirectionalMap<>();
    myFilesToRefresh = new HashSet<>();
  }

  public void register(@NotNull VirtualFile key, @NotNull final String url) {
    synchronized (myLock) {
      myFilesToRefresh.remove(key);
      myId2Key.put(normalizeId(url), key);
      if (myId2Key.size() > 10000) {
        LOG.info("Too many schema definitions registered. Something could go wrong.");
      }
    }
  }

  public void reset() {
    synchronized (myLock) {
      myFilesToRefresh.addAll(myId2Key.values());
      myId2Key.clear();
    }
  }

  public void dropKey(@NotNull VirtualFile key) {
    synchronized (myLock) {
      myFilesToRefresh.add(key);
      myId2Key.removeValue(key);
    }
  }

  @Nullable
  public VirtualFile getSchemaFileById(@NotNull final String id, JsonSchemaService service) {
    for (int i = 0; i < 100; i++) {
      final Set<VirtualFile> toRefresh = new HashSet<>();
      synchronized (myLock) {
        toRefresh.addAll(myFilesToRefresh);
        myFilesToRefresh.clear();
      }
      if (!toRefresh.isEmpty()) service.refreshSchemaIds(toRefresh);
      synchronized (myLock) {
        if (myFilesToRefresh.isEmpty()) break;
      }
    }

    synchronized (myLock) {
      return myId2Key.get(id);
    }
  }

  @NotNull
  public static String normalizeId(@NotNull String id) {
    id = id.endsWith("#") ? id.substring(0, id.length() - 1) : id;
    return id.startsWith("#") ? id.substring(1) : id;
  }
}
