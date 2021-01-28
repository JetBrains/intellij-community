// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.builders.java.dependencyView;

import com.intellij.openapi.util.Pair;
import gnu.trove.TObjectObjectProcedure;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
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

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {

    final List<Pair<K, String>> keys = new ArrayList<>();
    forEachEntry(new TObjectObjectProcedure<K, Collection<V>>() {
      @Override
      public boolean execute(final K a, final Collection<V> b) {
        keys.add(Pair.create(a, debugString(a)));
        return true;
      }
    });

    keys.sort(Pair.comparingBySecond());

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

  @NotNull
  protected String debugString(K k) {
    return k.toString();
  }
}
