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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Consumer;
import com.intellij.util.PairConsumer;
import com.intellij.util.containers.BidirectionalMap;
import com.intellij.util.containers.MultiMap;
import com.jetbrains.jsonSchema.extension.SchemaType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.jetbrains.jsonSchema.impl.JsonSchemaReader.LOG;

/**
 * @author Irina.Chernushina on 3/28/2016.
 */
public class JsonSchemaExportedDefinitions {
  private final Object myLock;
  private boolean myInitialized;
  private boolean myDirty;
  private final BidirectionalMap<String, Pair<SchemaType, ?>> myId2Key;
  private final MultiMap<Pair<SchemaType, ?>, Pair<SchemaType, ?>> myCrossDependencies;
  private final Map<String, Map<String, JsonSchemaObject>> myMap;
  private final Project myProject;
  @NotNull private final Consumer<PairConsumer<Pair<SchemaType, ?>, Consumer<Consumer<JsonSchemaObject>>>> mySchemasIterator;

  public JsonSchemaExportedDefinitions(@Nullable final Project project,
                                       @NotNull Consumer<PairConsumer<Pair<SchemaType, ?>, Consumer<Consumer<JsonSchemaObject>>>> schemasIterator) {
    myProject = project;
    mySchemasIterator = schemasIterator;
    myLock = new Object();
    myMap = new HashMap<>();
    myId2Key = new BidirectionalMap<>();
    myCrossDependencies = new MultiMap<>();
  }

  public void register(@NotNull Pair<SchemaType, ?> key, @NotNull final String url, @NotNull final Map<String, JsonSchemaObject> map) {
    synchronized (myLock) {
      myMap.put(url, map);
      myId2Key.put(url, key);
      if (myMap.size() > 10000) {
        LOG.info("Too many schema definitions registered. Something could go wrong.");
      }
    }
  }

  public JsonSchemaObject findDefinition(@NotNull Pair<SchemaType, ?> requestingSchemaKey, @NotNull final String url,
                                         @NotNull final String relativePart,
                                         @NotNull final JsonSchemaObject rootObject) {
    synchronized (myLock) {
      ensureInitialized();
      final Pair<SchemaType, ?> pair = myId2Key.get(url);
      if (pair != null) myCrossDependencies.putValue(pair, requestingSchemaKey);
      final Map<String, JsonSchemaObject> map = myMap.get(url);
      if (map != null) {
        return JsonSchemaReader.findDefinition(pair, relativePart, rootObject, map, null);
      }
    }
    return null;
  }

  private void ensureInitialized() {
    synchronized (myLock) {
      if (myInitialized && !myDirty) return;
      mySchemasIterator.consume(new PairConsumer<Pair<SchemaType, ?>, Consumer<Consumer<JsonSchemaObject>>>() {
        @Override
        public void consume(Pair<SchemaType, ?> key, Consumer<Consumer<JsonSchemaObject>> consumer) {
          if (!myInitialized || !myId2Key.containsValue(key)) {
            consumer.consume(new Consumer<JsonSchemaObject>() {
              @Override
              public void consume(JsonSchemaObject object) {
                JsonSchemaReader.registerObjectsExportedDefinitions(key, JsonSchemaExportedDefinitions.this, object);
              }
            });
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

  public Set<Pair<SchemaType, ?>> dropKey(@NotNull Pair<SchemaType, ?> key) {
    final Set<Pair<SchemaType, ?>> dirtyKeys = new HashSet<>();
    synchronized (myLock) {
      myDirty = true;
      final ArrayDeque<Pair<SchemaType, ?>> queue = new ArrayDeque<>();
      queue.add(key);
      while (!queue.isEmpty()) {
        final Pair<SchemaType, ?> current = queue.remove();
        dirtyKeys.add(current);
        final List<String> keys = myId2Key.getKeysByValue(current);
        myId2Key.removeValue(current);
        if (keys != null && !keys.isEmpty()) {
          assert keys.size() == 1;
          myMap.remove(keys.get(0));
          final Collection<Pair<SchemaType, ?>> dependencies = myCrossDependencies.remove(current);
          if (dependencies != null) {
            queue.addAll(dependencies);
          }
        }
      }
    }
    return dirtyKeys;
  }
}
