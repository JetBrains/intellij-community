// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.openapi.util.NotNullLazyValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author peter
 */
public abstract class LazyQuery<T> implements Query<T> {
  private final NotNullLazyValue<Query<T>> myQuery = new NotNullLazyValue<Query<T>>() {
    @Override
    @NotNull
    protected Query<T> compute() {
      return computeQuery();
    }
  };

  @NotNull protected abstract Query<T> computeQuery();

  @Override
  @NotNull
  public Collection<T> findAll() {
    return myQuery.getValue().findAll();
  }

  @Override
  public T findFirst() {
    return myQuery.getValue().findFirst();
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    return myQuery.getValue().forEach(consumer);
  }

  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    return myQuery.getValue().toArray(a);
  }

  @Override
  public Iterator<T> iterator() {
    return myQuery.getValue().iterator();
  }
}
