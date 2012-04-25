/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.ether.dependencyView;

import gnu.trove.TIntObjectProcedure;

import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 03.11.11
 * Time: 21:01
 * To change this template use File | Settings | File Templates.
 */
abstract class IntObjectMultiMaplet<V extends StringBufferizable> implements StringBufferizable {
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

  abstract void close();

  abstract void forEachEntry(TIntObjectProcedure<Collection<V>> procedure);

  abstract void flush(boolean memoryCachesOnly);

  public void toBuffer(final DependencyContext context, final StringBuffer buf) {
    forEachEntry(new TIntObjectProcedure<Collection<V>>() {
      @Override
      public boolean execute(final int a, final Collection<V> b) {
        buf.append("  Key: ");
        buf.append(context.getValue(a));
        buf.append("\n  Values:\n");

        for (final V value : b) {
          value.toBuffer(context, buf);
        }

        buf.append("  End Of Values\n");
        return true;
      }
    });
  }
}
