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
package com.intellij.openapi.editor.ex;

import com.intellij.util.containers.PeekableIterator;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.NoSuchElementException;

/**
 * An iterator you must {@link #dispose()} after use
 */
public interface MarkupIterator<T> extends PeekableIterator<T> {
  void dispose();

  MarkupIterator EMPTY = new MarkupIterator() {
    @Override
    public void dispose() {
    }

    @Override
    public Object peek() {
      throw new NoSuchElementException();
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Object next() {
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new NoSuchElementException();
    }
  };

  @NotNull
  static <T> MarkupIterator<T> mergeIterators(@NotNull final MarkupIterator<T> iterator1,
                                              @NotNull final MarkupIterator<T> iterator2,
                                              @NotNull final Comparator<? super T> comparator) {
    return new MarkupIterator<T>() {
      @Override
      public void dispose() {
        iterator1.dispose();
        iterator2.dispose();
      }

      @Override
      public boolean hasNext() {
        return iterator1.hasNext() || iterator2.hasNext();
      }

      @Override
      public T next() {
        return choose().next();
      }

      @NotNull
      private MarkupIterator<T> choose() {
        T t1 = iterator1.hasNext() ? iterator1.peek() : null;
        T t2 = iterator2.hasNext() ? iterator2.peek() : null;
        if (t1 == null) {
          return iterator2;
        }
        if (t2 == null) {
          return iterator1;
        }
        int compare = comparator.compare(t1, t2);
        return compare < 0 ? iterator1 : iterator2;
      }

      @Override
      public void remove() {
        throw new NoSuchElementException();
      }

      @Override
      public T peek() {
        return choose().peek();
      }
    };
  }
}
