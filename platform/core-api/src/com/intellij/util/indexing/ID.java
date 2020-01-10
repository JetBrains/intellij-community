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

import com.intellij.ide.plugins.PluginUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import gnu.trove.THashMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collections;
import java.util.Map;

/**
 * @author Eugene Zhuravlev
 */
public class ID<K, V> extends IndexId<K,V> {
  private static final IntObjectMap<ID> ourRegistry = ContainerUtil.createConcurrentIntObjectMap();
  private static final TObjectIntHashMap<String> ourNameToIdRegistry = new TObjectIntHashMap<>();

  private static final Map<ID, PluginId> ourIdToPluginId = Collections.synchronizedMap(new THashMap<>());
  static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  private final short myUniqueId;

  static {
    final File indices = getEnumFile();
    try {
      TObjectIntHashMap<String> nameToIdRegistry = new TObjectIntHashMap<>();
      try (BufferedReader reader = new BufferedReader(new FileReader(indices))) {
        for (int cnt = 1; ; cnt++) {
          final String name = reader.readLine();
          if (name == null) break;
          nameToIdRegistry.put(name, cnt);
        }
      }

      synchronized (ourNameToIdRegistry) {
        ourNameToIdRegistry.ensureCapacity(nameToIdRegistry.size());
        nameToIdRegistry.forEachEntry((name, index) -> {
          ourNameToIdRegistry.put(name, index);
          return true;
        });
      }
    }
    catch (IOException e) {
      synchronized (ourNameToIdRegistry) {
        ourNameToIdRegistry.clear();
        writeEnumFile();
      }
    }
  }

  @NotNull
  private static File getEnumFile() {
    final File indexFolder = PathManager.getIndexRoot();
    return new File(indexFolder, "indices.enum");
  }

  @ApiStatus.Internal
  protected ID(@NotNull String name, @Nullable PluginId pluginId) {
    super(name);
    myUniqueId = stringToId(name);

    final ID old = ourRegistry.put(myUniqueId, this);
    assert old == null : "ID with name '" + name + "' is already registered";

    PluginId oldPluginId = ourIdToPluginId.put(this, pluginId);
    assert oldPluginId == null : "ID with name '" + name + "' is already registered in " + oldPluginId + " but current caller is " + pluginId;
  }

  private static short stringToId(@NotNull String name) {
    synchronized (ourNameToIdRegistry) {
      if (ourNameToIdRegistry.containsKey(name)) {
        return (short)ourNameToIdRegistry.get(name);
      }

      int n = ourNameToIdRegistry.size() + 1;
      assert n <= MAX_NUMBER_OF_INDICES : "Number of indices exceeded: "+n;

      ourNameToIdRegistry.put(name, n);
      writeEnumFile();
      return (short)n;
    }
  }

  static void reinitializeDiskStorage() {
    synchronized (ourNameToIdRegistry) {
      writeEnumFile();
    }
  }

  private static void writeEnumFile() {
    try {
      final File f = getEnumFile();
      try (BufferedWriter w = new BufferedWriter(new FileWriter(f))) {
        final String[] names = new String[ourNameToIdRegistry.size()];

        ourNameToIdRegistry.forEachEntry((key, value) -> {
          names[value - 1] = key;
          return true;
        });

        for (String name : names) {
          w.write(name);
          w.newLine();
        }
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  public static synchronized <K, V> ID<K, V> create(@NonNls @NotNull String name) {
    PluginId pluginId = getCallerPluginId();
    final ID<K, V> found = findByName(name, true, pluginId);
    return found == null ? new ID<>(name, pluginId) : found;
  }

  @Nullable
  public static <K, V> ID<K, V> findByName(@NotNull String name) {
    return findByName(name, false, null);
  }

  @ApiStatus.Internal
  @Nullable
  protected static <K, V> ID<K, V> findByName(@NotNull String name,
                                              boolean checkCallerPlugin,
                                              @Nullable PluginId requiredPluginId) {
    ID<K, V> id = (ID<K, V>)findById(stringToId(name));
    if (checkCallerPlugin && id != null) {
      PluginId actualPluginId = ourIdToPluginId.get(id);

      String actualPluginIdStr = actualPluginId == null ? null : actualPluginId.getIdString();
      String requiredPluginIdStr = requiredPluginId == null ? null : requiredPluginId.getIdString();


      if (!Comparing.equal(actualPluginIdStr, requiredPluginIdStr)) {
        throw new AssertionError("ID with name '" + name + "' requested for plugin " + requiredPluginIdStr + " but registered for " + actualPluginIdStr);
      }
    }
    return id;
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

  @ApiStatus.Internal
  @Nullable
  protected static PluginId getCallerPluginId() {
    return PluginUtil.getInstance().getCallerPlugin(4);
  }

  @ApiStatus.Internal
  public static void clearIdRegistry() {
    ourRegistry.clear();
    ourIdToPluginId.clear();
  }
}
