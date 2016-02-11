/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.comparison.iterables;

import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

abstract class DiffIterableBase implements DiffIterable {
  @NotNull
  @Override
  public Iterable<Range> iterateUnchanged() {
    return new Iterable<Range>() {
      @Override
      public Iterator<Range> iterator() {
        return unchanged();
      }
    };
  }

  @NotNull
  @Override
  public Iterable<Range> iterateChanges() {
    return new Iterable<Range>() {
      @Override
      public Iterator<Range> iterator() {
        return changes();
      }
    };
  }

  protected static abstract class MyIterator<T> implements Iterator<T> {
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
