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

import com.intellij.openapi.util.NullableLazyValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.jetbrains.jsonSchema.impl.JsonSchemaReader.LOG;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaExportedDefinitions {
  private final Object myLock;
  private boolean myInitialized;
  private boolean myDirty;
  private final BidirectionalMap<String, VirtualFile> myId2Key;
  private final MultiMap<VirtualFile, VirtualFile> myCrossDependencies;
  private final Map<String, Map<String, JsonSchemaObject>> myMap;
  @NotNull private final Consumer<PairConsumer<VirtualFile, NullableLazyValue<JsonSchemaObject>>> mySchemasIterator;

  public JsonSchemaExportedDefinitions(@NotNull Consumer<PairConsumer<VirtualFile, NullableLazyValue<JsonSchemaObject>>> schemasIterator) {
    mySchemasIterator = schemasIterator;
    myLock = new Object();
    myMap = new HashMap<>();
    myId2Key = new BidirectionalMap<>();
    myCrossDependencies = new MultiMap<>();
  }

  public void register(@NotNull VirtualFile key, @NotNull final String url, @NotNull final Map<String, JsonSchemaObject> map) {
    synchronized (myLock) {
      myMap.put(url, map);
      myId2Key.put(url, key);
      if (myMap.size() > 10000) {
        LOG.info("Too many schema definitions registered. Something could go wrong.");
      }
    }
  }

  public JsonSchemaObject findDefinition(@NotNull VirtualFile requestingSchemaKey, @NotNull final String url,
                                         @NotNull final String relativePart) {
    synchronized (myLock) {
      ensureInitialized();
      final VirtualFile key = myId2Key.get(url);
      if (key != null) myCrossDependencies.putValue(key, requestingSchemaKey);
      final Map<String, JsonSchemaObject> map = myMap.get(url);
      if (map != null) {
        final JsonSchemaObject found = map.get(relativePart);
        if (found != null) return found;
      }
    }
    return null;
  }

  private void ensureInitialized() {
    synchronized (myLock) {
      if (myInitialized && !myDirty) return;
      mySchemasIterator.consume((key, value) -> {
        if (!myInitialized || !myId2Key.containsValue(key)) {
          final JsonSchemaObject object = value.getValue();
          if (object != null) {
            JsonSchemaReader.registerObjectsExportedDefinitions(key, this, object);
          }
        }
      });
      myDirty = false;
      myInitialized = true;
    }
  }

  public void reset() {
    synchronized (myLock) {
      myInitialized = false;
      myDirty = false;
      myMap.clear();
      myCrossDependencies.clear();
      myId2Key.clear();
    }
  }

  public Set<VirtualFile> dropKey(@NotNull VirtualFile key) {
    final Set<VirtualFile> dirtyKeys = new HashSet<>();
    synchronized (myLock) {
      myDirty = true;
      final ArrayDeque<VirtualFile> queue = new ArrayDeque<>();
      queue.add(key);
      while (!queue.isEmpty()) {
        final VirtualFile current = queue.remove();
        dirtyKeys.add(current);
        final List<String> keys = myId2Key.getKeysByValue(current);
        myId2Key.removeValue(current);
        if (keys != null && !keys.isEmpty()) {
          assert keys.size() == 1;
          myMap.remove(keys.get(0));
          final Collection<VirtualFile> dependencies = myCrossDependencies.remove(current);
          if (dependencies != null) {
            queue.addAll(dependencies);
          }
        }
      }
    }
    return dirtyKeys;
  }

  public boolean checkFileForId(@NotNull final String id, @NotNull final VirtualFile file) {
    synchronized (myLock) {
      ensureInitialized();
      return file.equals(myId2Key.get(id));
    }
  }
}
