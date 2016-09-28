/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.diff.util;

import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum ThreeSide {
  LEFT(0),
  BASE(1),
  RIGHT(2);

  private final int myIndex;

  ThreeSide(int index) {
    myIndex = index;
  }

  @NotNull
  public static ThreeSide fromIndex(int index) {
    if (index == 0) return LEFT;
    if (index == 1) return BASE;
    if (index == 2) return RIGHT;
    throw new IndexOutOfBoundsException("index: " + index);
  }

  public int getIndex() {
    return myIndex;
  }

  //
  // Helpers
  //

  @Nullable
  @Contract("!null, !null, !null -> !null; null, null, null -> null")
  public <T> T select(@Nullable T left, @Nullable T base, @Nullable T right) {
    if (myIndex == 0) return left;
    if (myIndex == 1) return base;
    if (myIndex == 2) return right;
    //noinspection Contract
    throw new IllegalStateException();
  }

  @NotNull
  public <T> T selectNotNull(@NotNull T left, @NotNull T base, @NotNull T right) {
    if (myIndex == 0) return left;
    if (myIndex == 1) return base;
    if (myIndex == 2) return right;
    throw new IllegalStateException();
  }

  public int select(@NotNull int[] array) {
    assert array.length == 3;
    return array[myIndex];
  }

  public <T> T select(@NotNull T[] array) {
    assert array.length == 3;
    return array[myIndex];
  }

  @NotNull
  public <T> T selectNotNull(@NotNull T[] array) {
    assert array.length == 3;
    return array[myIndex];
  }

  public <T> T select(@NotNull List<T> list) {
    assert list.size() == 3;
    return list.get(myIndex);
  }

  @NotNull
  public <T> T selectNotNull(@NotNull List<T> list) {
    assert list.size() == 3;
    return list.get(myIndex);
  }

  @Nullable
  public static <T> ThreeSide fromValue(@NotNull List<? extends T> list, @Nullable T value) {
    assert list.size() == 3;
    int index = list.indexOf(value);
    return index != -1 ? fromIndex(index) : null;
  }

  @NotNull
  public static <T> List<T> map(@NotNull Function<ThreeSide, T> function) {
    return ContainerUtil.list(
      function.fun(LEFT),
      function.fun(BASE),
      function.fun(RIGHT)
    );
  }
}
