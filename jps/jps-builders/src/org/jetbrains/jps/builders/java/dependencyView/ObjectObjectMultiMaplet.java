/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import gnu.trove.TObjectObjectProcedure;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.*;

/**
 * @author: db
 */
abstract class ObjectObjectMultiMaplet<K, V> implements Streamable, CloseableMaplet {
  abstract boolean containsKey(final K key);

  abstract Collection<V> get(final K key);

  abstract void put(final K key, final V value);

  abstract void put(final K key, final Collection<V> value);

  abstract void replace(final K key, final Collection<V> value);

  abstract void putAll(ObjectObjectMultiMaplet<K, V> m);

  abstract void replaceAll(ObjectObjectMultiMaplet<K, V> m);

  abstract void remove(final K key);

  abstract void removeFrom(final K key, final V value);

  abstract void removeAll(final K key, final Collection<V> value);

  abstract void forEachEntry(TObjectObjectProcedure<K, Collection<V>> procedure);

  abstract void flush(boolean memoryCachesOnly);

  public void toStream(final DependencyContext context, final PrintStream stream) {

    final List<Pair<K, String>> keys = new ArrayList<>();
    forEachEntry(new TObjectObjectProcedure<K, Collection<V>>() {
      @Override
      public boolean execute(final K a, final Collection<V> b) {
        // on case-insensitive file systems save paths in normalized (lowercase) format in order to make tests run deterministically
        final String keyStr = a instanceof File && !SystemInfo.isFileSystemCaseSensitive?
                              ((File)a).getPath().toLowerCase(Locale.US) :
                              a.toString();
        keys.add(Pair.create(a, keyStr));
        return true;
      }
    });

    keys.sort(Comparator.comparing(o -> o.second));

    for (final Pair<K, String> a: keys) {
      final Collection<V> b = get(a.first);

      stream.print("  Key: ");
      stream.println(a.second);
      stream.println("  Values:");

      final List<String> list = new LinkedList<>();

      for (final V value : b) {
        if (value instanceof Streamable) {
          final ByteArrayOutputStream baos = new ByteArrayOutputStream();
          final PrintStream s = new PrintStream(baos);
          ((Streamable) value).toStream(context, s);
          list.add(baos.toString());
        }
      }

      Collections.sort(list);

      for (final String l : list) {
        stream.print(l);
      }

      stream.println("  End Of Values");
    }
  }

}
