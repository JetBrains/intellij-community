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

import gnu.trove.TIntObjectProcedure;

import java.io.PrintStream;

/**
 * @author: db
 * Date: 04.11.11
 */
abstract class IntObjectMaplet<V> implements Streamable {
  abstract boolean containsKey(final int key);

  abstract V get(final int key);

  abstract void put(final int key, final V value);

  abstract void putAll(IntObjectMaplet<V> m);

  abstract void remove(final int key);

  abstract void close();

  abstract void forEachEntry(TIntObjectProcedure<V> proc);

  abstract void flush(boolean memoryCachesOnly);

  public void toStream(final DependencyContext context, final PrintStream stream) {
    final OrderProvider op = new OrderProvider(context);

    forEachEntry(new TIntObjectProcedure<V>() {
      @Override
      public boolean execute(final int a, final V b) {
        op.register(a);
        return true;
      }
    });

    final int[] keys = op.get();

    for (final int a : keys) {
      final V b = get(a);

      stream.print("  ");
      stream.print(context.getValue(a));
      stream.print(" -> ");

      stream.print(b.toString());
    }
  }
}
