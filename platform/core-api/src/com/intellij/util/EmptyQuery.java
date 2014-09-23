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
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

/**
 * @author max
 */
public class EmptyQuery<R> implements Query<R> {
  private static final EmptyQuery EMPTY_QUERY_INSTANCE = new EmptyQuery();

  @Override
  @NotNull
  public Collection<R> findAll() {
    return Collections.emptyList();
  }

  @Override
  public R findFirst() {
    return null;
  }

  @Override
  public boolean forEach(@NotNull Processor<R> consumer) {
    return true;
  }

  @NotNull
  @Override
  public AsyncFuture<Boolean> forEachAsync(@NotNull Processor<R> consumer) {
    return AsyncUtil.wrapBoolean(true);
  }

  @NotNull
  @Override
  public R[] toArray(@NotNull R[] a) {
    return findAll().toArray(a);
  }

  @Override
  public Iterator<R> iterator() {
    return findAll().iterator();
  }

  public static <T> Query<T> getEmptyQuery() {
    @SuppressWarnings("unchecked") Query<T> instance = (Query<T>)EMPTY_QUERY_INSTANCE;
    return instance;
  }
}
