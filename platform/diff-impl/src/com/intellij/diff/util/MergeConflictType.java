/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

public class MergeConflictType {
  @NotNull private final Type myType;
  private final boolean myLeftChange;
  private final boolean myRightChange;
  private final boolean myCanBeResolved;

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange) {
    this(type, leftChange, rightChange, true);
  }

  public MergeConflictType(@NotNull Type type, boolean leftChange, boolean rightChange, boolean canBeResolved) {
    myType = type;
    myLeftChange = leftChange;
    myRightChange = rightChange;
    myCanBeResolved = canBeResolved;
  }

  @NotNull
  public Type getType() {
    return myType;
  }

  public boolean canBeResolved() {
    return myCanBeResolved;
  }

  public boolean isChange(@NotNull Side side) {
    return side.isLeft() ? myLeftChange : myRightChange;
  }

  public boolean isChange(@NotNull ThreeSide side) {
    switch (side) {
      case LEFT:
        return myLeftChange;
      case BASE:
        return true;
      case RIGHT:
        return myRightChange;
      default:
        throw new IllegalArgumentException(side.toString());
    }
  }

  public enum Type {INSERTED, DELETED, MODIFIED, CONFLICT}
}
