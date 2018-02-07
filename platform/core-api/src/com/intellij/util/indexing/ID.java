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

import com.intellij.openapi.application.PathManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.IntObjectMap;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 */
public class ID<K, V> extends IndexId<K,V> {
  private static final IntObjectMap<ID> ourRegistry = ContainerUtil.createConcurrentIntObjectMap();
  private static final TObjectIntHashMap<String> ourNameToIdRegistry = new TObjectIntHashMap<>();
  static final int MAX_NUMBER_OF_INDICES = Short.MAX_VALUE;

  private final short myUniqueId;

  static {
    final File indices = getEnumFile();
    try {
      TObjectIntHashMap<String> nameToIdRegistry = new TObjectIntHashMap<>();
      try (BufferedReader reader = new BufferedReader(new FileReader(indices))) {
        int cnt = 0;
        do {
          cnt++;
          final String name = reader.readLine();
          if (name == null) break;
          nameToIdRegistry.put(name, cnt);
        }
        while (true);
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

  private static File getEnumFile() {
    final File indexFolder = PathManager.getIndexRoot();
    return new File(indexFolder, "indices.enum");
  }

  protected ID(String name) {
    super(name);
    myUniqueId = stringToId(name);

    final ID old = ourRegistry.put(myUniqueId, this);
    assert old == null : "ID with name '" + name + "' is already registered";
  }

  private static short stringToId(String name) {
    synchronized (ourNameToIdRegistry) {
      if (ourNameToIdRegistry.containsKey(name)) {
        return (short)ourNameToIdRegistry.get(name);
      }

      int n = ourNameToIdRegistry.size() + 1;
      assert n <= MAX_NUMBER_OF_INDICES : "Number of indices exceeded";

      ourNameToIdRegistry.put(name, n);
      writeEnumFile();
      return (short)n;
    }
  }

  public static void reinitializeDiskStorage() {
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
  public static <K, V> ID<K, V> create(@NonNls @NotNull String name) {
    final ID<K, V> found = findByName(name);
    return found != null ? found : new ID<>(name);
  }

  @Nullable
  public static <K, V> ID<K, V> findByName(@NotNull String name) {
    return (ID<K, V>)findById(stringToId(name));
  }

  public int hashCode() {
    return (int)myUniqueId;
  }

  /**
   * Consider to use {@link ID#getName()} instead of this method
   */
  public String toString() {
    return getName();
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  public static ID<?, ?> findById(int id) {
    return ourRegistry.get(id);
  }
}
