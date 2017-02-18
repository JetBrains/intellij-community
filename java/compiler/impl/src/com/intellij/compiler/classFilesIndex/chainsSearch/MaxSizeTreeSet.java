/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.compiler.classFilesIndex.chainsSearch;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class MaxSizeTreeSet<E> implements NavigableSet<E> {
  @NotNull
  private final NavigableSet<E> myUnderlying;
  private final int myMaxSize;

  public MaxSizeTreeSet(final int maxSize) {
    myMaxSize = maxSize;
    if (myMaxSize < 1) {
      throw new IllegalArgumentException();
    }
    myUnderlying = new TreeSet<>();
  }

  public E lower(final E e) {
    return myUnderlying.lower(e);
  }

  public E floor(final E e) {
    return myUnderlying.floor(e);
  }

  public E ceiling(final E e) {
    return myUnderlying.ceiling(e);
  }

  public E higher(final E e) {
    return myUnderlying.higher(e);
  }

  @Override
  public E pollFirst() {
    return myUnderlying.pollFirst();
  }

  @Override
  public E pollLast() {
    return myUnderlying.pollLast();
  }

  @NotNull
  @Override
  public Iterator<E> iterator() {
    return myUnderlying.iterator();
  }

  @NotNull
  @Override
  public NavigableSet<E> descendingSet() {
    return myUnderlying.descendingSet();
  }

  @NotNull
  @Override
  public Iterator<E> descendingIterator() {
    return myUnderlying.descendingIterator();
  }

  @NotNull
  public NavigableSet<E> subSet(final E fromElement, final boolean fromInclusive, final E toElement, final boolean toInclusive) {
    return myUnderlying.subSet(fromElement, fromInclusive, toElement, toInclusive);
  }

  @NotNull
  public NavigableSet<E> headSet(final E toElement, final boolean inclusive) {
    return myUnderlying.headSet(toElement, inclusive);
  }

  @NotNull
  public NavigableSet<E> tailSet(final E fromElement, final boolean inclusive) {
    return myUnderlying.tailSet(fromElement, inclusive);
  }

  @NotNull
  public SortedSet<E> subSet(final E fromElement, final E toElement) {
    return myUnderlying.subSet(fromElement, toElement);
  }

  @NotNull
  public SortedSet<E> headSet(final E toElement) {
    return myUnderlying.headSet(toElement);
  }

  @NotNull
  public SortedSet<E> tailSet(final E fromElement) {
    return myUnderlying.tailSet(fromElement);
  }

  @Nullable
  @Override
  public Comparator<? super E> comparator() {
    return myUnderlying.comparator();
  }

  @Override
  public E first() {
    return myUnderlying.first();
  }

  @Override
  public E last() {
    return myUnderlying.last();
  }

  @Override
  public int size() {
    return myUnderlying.size();
  }

  @Override
  public boolean isEmpty() {
    return myUnderlying.isEmpty();
  }

  @Override
  public boolean contains(final Object o) {
    return myUnderlying.contains(o);
  }

  @NotNull
  @Override
  public Object[] toArray() {
    return myUnderlying.toArray();
  }

  @NotNull
  @Override
  public <T> T[] toArray(final T[] a) {
    return myUnderlying.toArray(a);
  }

  public boolean add(final E e) {
    if (myUnderlying.size() == myMaxSize) {
      //noinspection ConstantConditions
      final Comparator<? super E> comparator = comparator();
      if ((comparator == null ? ((Comparable)e).compareTo(last()) : comparator.compare(e, last())) < 0) {
        final boolean isAdded = myUnderlying.add(e);
        if (isAdded) {
          pollLast();
          return true;
        }
      }
      return false;
    }
    return myUnderlying.add(e);
  }

  @Override
  public boolean remove(final Object o) {
    return myUnderlying.remove(o);
  }

  @Override
  public boolean containsAll(final Collection<?> c) {
    return myUnderlying.containsAll(c);
  }

  public boolean addAll(final Collection<? extends E> c) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean retainAll(final Collection<?> c) {
    return myUnderlying.retainAll(c);
  }

  @Override
  public boolean removeAll(final Collection<?> c) {
    return myUnderlying.removeAll(c);
  }

  @Override
  public void clear() {
    myUnderlying.clear();
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (!(o instanceof MaxSizeTreeSet)) return false;

    final MaxSizeTreeSet that = (MaxSizeTreeSet)o;

    if (!myUnderlying.equals(that.myUnderlying)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return myUnderlying.hashCode();
  }

  @Override
  public String toString() {
    return myUnderlying.toString();
  }
}
