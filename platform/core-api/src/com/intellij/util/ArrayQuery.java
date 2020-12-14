// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;

public class ArrayQuery<T> implements Query<T> {
  private final T[] myArray;

  public ArrayQuery(T @NotNull ... array) {
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
}
