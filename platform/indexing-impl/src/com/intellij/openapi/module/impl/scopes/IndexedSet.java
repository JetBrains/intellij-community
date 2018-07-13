// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.module.impl.scopes;

import com.intellij.util.UnmodifiableIterator;
import com.intellij.util.containers.ObjectIntHashMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.function.Predicate;

class IndexedSet<E> extends LinkedHashSet<E> {

  private final ObjectIntHashMap<E> myIndex = new ObjectIntHashMap<>();

  /**
   * @return index of element or -1 if element is not in this set
   */
  int indexOf(E element) {
    return myIndex.get(element);
  }

  @Override
  public boolean add(E e) {
    if (super.add(e)) {
      myIndex.put(e, size() - 1);
      return true;
    }
    else {
      return false;
    }
  }

  @Override
  public Iterator<E> iterator() {
    return new UnmodifiableIterator<>(super.iterator());
  }

  @Override
  public boolean remove(Object o) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(Collection<?> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean removeIf(Predicate<? super E> filter) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object clone() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    return myIndex.equals(((IndexedSet<?>)o).myIndex);
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myIndex.hashCode();
  }
}
