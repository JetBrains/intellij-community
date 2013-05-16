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

package com.intellij.util.indexing;

import com.intellij.openapi.application.PathManager;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectIntHashMap;
import gnu.trove.TObjectIntProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Feb 12, 2008
 */
public class ID<K, V> {
  private static final TIntObjectHashMap<ID> ourRegistry = new TIntObjectHashMap<ID>();
  private static final TObjectIntHashMap<String> ourNameToIdRegistry = new TObjectIntHashMap<String>();

  private final String myName;
  private final short myUniqueId;

  static {
    final File indices = getEnumFile();
    try {
      final BufferedReader reader = new BufferedReader(new FileReader(indices));
      try {
        int cnt = 0;
        do {
            cnt++;
            final String name = reader.readLine();
            if (name == null) break;
            ourNameToIdRegistry.put(name, cnt);
          }
          while (true);
      }
      finally {
        reader.close();
      }
    }
    catch (IOException e) {
      ourNameToIdRegistry.clear();
      writeEnumFile();
    }
  }

  private static File getEnumFile() {
    final File indexFolder = PathManager.getIndexRoot();
    return new File(indexFolder, "indices.enum");
  }

  protected ID(String name) {
    myName = name;
    myUniqueId = stringToId(name);

    final ID old = ourRegistry.put(myUniqueId, this);
    assert old == null;
  }

  private static short stringToId(String name) {
    if (ourNameToIdRegistry.containsKey(name)) {
      return (short)ourNameToIdRegistry.get(name);
    }

    int n = ourNameToIdRegistry.size() + 1;
    assert n <= Short.MAX_VALUE : "Number of indices exceeded";

    ourNameToIdRegistry.put(name, n);

    writeEnumFile();

    return (short)n;
  }

  private static void writeEnumFile() {
    try {
      final File f = getEnumFile();
      final BufferedWriter w = new BufferedWriter(new FileWriter(f));
      try {
        final String[] names = new String[ourNameToIdRegistry.size()];

        ourNameToIdRegistry.forEachEntry(new TObjectIntProcedure<String>() {
            @Override
            public boolean execute(final String key, final int value) {
              names[value - 1] = key;
              return true;
            }
          });

        for (String name : names) {
            w.write(name);
            w.newLine();
          }
      }
      finally {
        w.close();
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static <K, V> ID<K, V> create(@NonNls String name) {
    final ID<K, V> found = findByName(name);
    return found != null ? found : new ID<K, V>(name);
  }

  @Nullable
  public static <K, V> ID<K, V> findByName(@NotNull String name) {
    return (ID<K, V>)findById(stringToId(name));
  }

  public int hashCode() {
    return (int)myUniqueId;
  }

  public String toString() {
    return myName;
  }

  public int getUniqueId() {
    return myUniqueId;
  }

  public static ID<?, ?> findById(int id) {
    return ourRegistry.get(id);
  }
}
