// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

    @Override
    public String toString() {
      return "EMPTY";
    }
  };

  static @NotNull <T> MarkupIterator<T> mergeIterators(final @NotNull MarkupIterator<T> iterator1,
                                                       final @NotNull MarkupIterator<T> iterator2,
                                                       final @NotNull Comparator<? super T> comparator) {
    if (iterator1 == MarkupIterator.EMPTY) {
      return iterator2;
    }
    if (iterator2 == MarkupIterator.EMPTY) {
      return iterator1;
    }
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

      private @NotNull MarkupIterator<T> choose() {
        T t1 = iterator1.hasNext() ? iterator1.peek() : null;
        if (t1 == null) {
          return iterator2;
        }
        T t2 = iterator2.hasNext() ? iterator2.peek() : null;
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
