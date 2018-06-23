// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.concurrency.AsyncFuture;
import com.intellij.concurrency.AsyncUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author max
 */
public class ArrayQuery<T> implements Query<T> {
  private final T[] myArray;

  public ArrayQuery(@NotNull T... array) {
    myArray = array;
  }

  @Override
  @NotNull
  public Collection<T> findAll() {
    return Arrays.asList(myArray);
  }

  @Override
  public T findFirst() {
    return myArray.length > 0 ? myArray[0] : null;
  }

  @Override
  public boolean forEach(@NotNull final Processor<? super T> consumer) {
    return ContainerUtil.process(myArray, consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull final Processor<? super T> consumer) {
    return AsyncUtil.wrapBoolean(forEach(consumer));
  }


  @NotNull
  @Override
  public T[] toArray(@NotNull final T[] a) {
    return myArray;
  }

  @Override
  public Iterator<T> iterator() {
    return Arrays.asList(myArray).iterator();
  }
}
