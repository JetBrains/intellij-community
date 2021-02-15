// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;

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
}
