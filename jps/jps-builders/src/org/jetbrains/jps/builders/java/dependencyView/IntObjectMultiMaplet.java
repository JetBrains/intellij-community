// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.ObjIntConsumer;

abstract class IntObjectMultiMaplet<V> implements Streamable, CloseableMaplet {
  abstract boolean containsKey(final int key);

  abstract Collection<V> get(final int key);

  abstract void put(final int key, final V value);

  abstract void put(final int key, final Collection<V> value);

  abstract void replace(final int key, final Collection<V> value);

  abstract void putAll(IntObjectMultiMaplet<V> m);

  abstract void replaceAll(IntObjectMultiMaplet<V> m);

  abstract void remove(final int key);

  abstract void removeFrom(final int key, final V value);

  abstract void removeAll(final int key, final Collection<V> value);

  abstract void forEachEntry(ObjIntConsumer<? super Collection<V>> procedure);

  abstract void flush(boolean memoryCachesOnly);

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    final OrderProvider op = new OrderProvider(context);

    forEachEntry((vs, value) -> op.register(value));

    final int[] keys = op.get();

    for (final int a : keys) {
      final Collection<V> b = get(a);

      stream.print("  Key: ");
      stream.println(context.getValue(a));
      stream.println("  Values:");

      final List<String> list = new LinkedList<>();

      for (final V value : b) {
        if (value instanceof Streamable) {
          final ByteArrayOutputStream baos = new ByteArrayOutputStream();
          final PrintStream s = new PrintStream(baos);

          ((Streamable)value).toStream(context, s);

          list.add(baos.toString());
        }
        else {
          list.add(value.toString());
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
