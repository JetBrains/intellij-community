/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import gnu.trove.TIntHashSet;
import gnu.trove.TIntObjectProcedure;
import gnu.trove.TIntProcedure;

import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * @author: db
 */
abstract class IntIntMultiMaplet implements Streamable, CloseableMaplet {
  abstract boolean containsKey(final int key);

  abstract TIntHashSet get(final int key);

  abstract void put(final int key, final int value);

  abstract void put(final int key, final TIntHashSet value);

  abstract void replace(final int key, final TIntHashSet value);

  abstract void putAll(IntIntMultiMaplet m);

  abstract void replaceAll(IntIntMultiMaplet m);

  abstract void remove(final int key);

  abstract void removeFrom(final int key, final int value);

  abstract void removeAll(final int key, final TIntHashSet values);

  abstract void forEachEntry(TIntObjectProcedure<TIntHashSet> proc);

  abstract void flush(boolean memoryCachesOnly);

  public void toStream(final DependencyContext context, final PrintStream stream) {
    final OrderProvider op = new OrderProvider(context);

    forEachEntry(new TIntObjectProcedure<TIntHashSet>() {
      @Override
      public boolean execute(final int a, final TIntHashSet b) {
        op.register(a);
        return true;
      }
    });

    final int[] keys = op.get();

    for (final int a : keys) {
      final TIntHashSet b = get(a);

      stream.print("  Key: ");
      stream.println(context.getValue(a));
      stream.println("  Values:");

      final List<String> list = new LinkedList<>();

      b.forEach(value -> {
        list.add(context.getValue(value));
        return true;
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
