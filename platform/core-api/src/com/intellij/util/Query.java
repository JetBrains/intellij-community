/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author max
 */
public interface Query<Result> extends Iterable<Result> {
  /**
   * Get all of the results in the {@link Collection}
   * @return results in a collection or empty collection if no results found.
   */
  @NotNull
  Collection<Result> findAll();

  /**
   * Get the first result or {@code null} if no results have been found.
   * @return first result of the search or {@code null} if no results.
   */
  @Nullable
  Result findFirst();

  /**
   * Process search results one-by-one. All the results will be subsequently fed to a {@code consumer} passed.
   * @param consumer - a processor search results should be fed to.
   * @return {@code true} if the search was completed normally,
   *         {@code false} if the occurrence processing was cancelled by the processor.
   */
  boolean forEach(@NotNull Processor<Result> consumer);

  @NotNull
  AsyncFuture<Boolean> forEachAsync(@NotNull Processor<Result> consumer);

  @NotNull
  Result[] toArray(@NotNull Result[] a);
}
