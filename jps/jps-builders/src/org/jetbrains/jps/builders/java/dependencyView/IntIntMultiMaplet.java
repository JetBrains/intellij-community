// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java.dependencyView;

import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.ObjIntConsumer;

abstract class IntIntMultiMaplet implements Streamable, CloseableMaplet {
  abstract boolean containsKey(final int key);

  abstract IntSet get(final int key);

  abstract void put(final int key, final int value);

  abstract void put(final int key, final IntSet value);

  abstract void replace(final int key, final IntSet value);

  abstract void putAll(IntIntMultiMaplet m);

  abstract void replaceAll(IntIntMultiMaplet m);

  abstract void remove(final int key);

  abstract void removeFrom(final int key, final int value);

  abstract void removeAll(final int key, final IntSet values);

  abstract void forEachEntry(ObjIntConsumer<? super IntSet> proc);

  abstract void flush(boolean memoryCachesOnly);

  @Override
  public void toStream(final DependencyContext context, final PrintStream stream) {
    final OrderProvider op = new OrderProvider(context);

    forEachEntry((integers, value) -> op.register(value));

    final int[] keys = op.get();

    for (final int a : keys) {
      final IntSet b = get(a);
      if (b == null) {
        continue;
      }
      
      stream.print("  Key: ");
      stream.println(context.getValue(a));
      stream.println("  Values:");

      final List<String> list = new LinkedList<>();

      b.forEach(value -> {
        list.add(context.getValue(value));
      });

      Collections.sort(list);

      for (final String l : list) {
        stream.print("    ");
        stream.println(l);
      }

      stream.println("  End Of Values");
    }
  }
}
