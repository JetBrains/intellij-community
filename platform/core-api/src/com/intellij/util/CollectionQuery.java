// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author max
 */
public class CollectionQuery<T> implements Query<T> {
  private final Collection<T> myCollection;

  public CollectionQuery(@NotNull final Collection<T> collection) {
    myCollection = collection;
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    return myCollection;
  }

  @Override
  public T findFirst() {
    final Iterator<T> i = iterator();
    return i.hasNext() ? i.next() : null;
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    return ContainerUtil.process(myCollection, consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<? super T> consumer) {
    return AsyncUtil.wrapBoolean(forEach(consumer));
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    return findAll().toArray(a);
  }

  @Override
  public Iterator<T> iterator() {
    return myCollection.iterator();
  }
}
