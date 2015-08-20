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

import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.LineFragment;
import com.intellij.openapi.util.Couple;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public enum Side {
  LEFT(0),
  RIGHT(1);

  private final int myIndex;

  Side(int index) {
    myIndex = index;
  }

  @NotNull
  public static Side fromIndex(int index) {
    if (index == 0) return LEFT;
    if (index == 1) return RIGHT;
    throw new IndexOutOfBoundsException("index: " + index);
  }

  @NotNull
  public static Side fromLeft(boolean isLeft) {
    return isLeft ? LEFT : RIGHT;
  }

  @NotNull
  public static Side fromRight(boolean isRight) {
    return isRight ? RIGHT : LEFT;
  }

  public int getIndex() {
    return myIndex;
  }

  public boolean isLeft() {
    return myIndex == 0;
  }

  @NotNull
  public Side other() {
    return isLeft() ? RIGHT : LEFT;
  }

  @NotNull
  public Side other(boolean other) {
    return other ? other() : this;
  }

  //
  // Helpers
  //

  public int select(int left, int right) {
    return isLeft() ? left : right;
  }

  @Nullable
  @Contract("!null, !null -> !null; null, null -> null")
  public <T> T select(@Nullable T left, @Nullable T right) {
    return isLeft() ? left : right;
  }

  @NotNull
  public <T> T selectNotNull(@NotNull T left, @NotNull T right) {
    return isLeft() ? left : right;
  }

  public boolean select(@NotNull boolean[] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  public int select(@NotNull int[] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  public <T> T select(@NotNull T[] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  @NotNull
  public <T> T selectNotNull(@NotNull T[] array) {
    assert array.length == 2;
    return array[myIndex];
  }

  public <T> T select(@NotNull List<T> list) {
    assert list.size() == 2;
    return list.get(myIndex);
  }

  @NotNull
  public <T> T selectNotNull(@NotNull List<T> list) {
    assert list.size() == 2;
    return list.get(myIndex);
  }

  public <T> T select(@NotNull Couple<T> region) {
    return isLeft() ? region.first : region.second;
  }

  @NotNull
  public <T> T selectNotNull(@NotNull Couple<T> region) {
    return isLeft() ? region.first : region.second;
  }

  //
  // Fragments
  //

  public int getStartOffset(@NotNull DiffFragment fragment) {
    return isLeft() ? fragment.getStartOffset1() : fragment.getStartOffset2();
  }

  public int getEndOffset(@NotNull DiffFragment fragment) {
    return isLeft() ? fragment.getEndOffset1() : fragment.getEndOffset2();
  }

  public int getStartLine(@NotNull LineFragment fragment) {
    return isLeft() ? fragment.getStartLine1() : fragment.getStartLine2();
  }

  public int getEndLine(@NotNull LineFragment fragment) {
    return isLeft() ? fragment.getEndLine1() : fragment.getEndLine2();
  }
}
