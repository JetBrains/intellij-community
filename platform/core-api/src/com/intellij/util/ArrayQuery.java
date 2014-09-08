/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
  public boolean forEach(@NotNull final Processor<T> consumer) {
    return ContainerUtil.process(myArray, consumer);
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull final Processor<T> consumer) {
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
