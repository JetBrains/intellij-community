/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.util.indexing;

import com.google.common.hash.Hashing;
import com.intellij.openapi.application.PathManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author Eugene Zhuravlev
 */
public class ID<K, V> extends IndexId<K,V> {
  private static final IntObjectMap<ID> ourRegistry = ContainerUtil.createConcurrentIntObjectMap();
  private static final TObjectIntHashMap<String> ourNameToIdRegistry = new TObjectIntHashMap<>();
  static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  private final int myUniqueId;

  protected ID(@NotNull String name) {
    super(name);
    myUniqueId = stringToId(name);

    final ID old = ourRegistry.put(myUniqueId, this);
    if (old != null) {
      throw new AssertionError("ID with name '" + name + "' is already registered");
    }
  }

  private static int stringToId(@NotNull String name) {
    synchronized (ourNameToIdRegistry) {
      if (ourNameToIdRegistry.containsKey(name)) {
        return ourNameToIdRegistry.get(name);
      }

      int uniqieId = Hashing.murmur3_32().hashString(name, StandardCharsets.UTF_8).asInt();
      ourNameToIdRegistry.put(name, uniqieId);
      return uniqieId;
    }
  }

  @NotNull
  public static <K, V> ID<K, V> create(@NonNls @NotNull String name) {
    final ID<K, V> found = findByName(name);
    return found == null ? new ID<>(name) : found;
  }

  @Nullable
  public static <K, V> ID<K, V> findByName(@NotNull String name) {
    return (ID<K, V>)findById(stringToId(name));
  }

  @Nullable
  public static <K, V> ID<K, V> findByHash(int hash) {
    return (ID<K, V>)findById(hash);
  }

  @Override
  public int hashCode() {
    return myUniqueId;
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  public static ID<?, ?> findById(int id) {
    return ourRegistry.get(id);
  }
}
