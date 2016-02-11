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
package com.intellij.diff.comparison.iterables;

import com.intellij.util.diff.Diff;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class DiffChangeDiffIterable extends ChangeDiffIterableBase {
  @Nullable private final Diff.Change myChange;

  public DiffChangeDiffIterable(@Nullable Diff.Change change, int length1, int length2) {
    super(length1, length2);
    myChange = change;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new DiffChangeChangeIterable(myChange);
  }

  @SuppressWarnings("ConstantConditions")
  private static class DiffChangeChangeIterable implements ChangeIterable {
    @Nullable private Diff.Change myChange;

    public DiffChangeChangeIterable(@Nullable Diff.Change change) {
      myChange = change;
    }

    @Override
    public boolean valid() {
      return myChange != null;
    }

    @Override
    public void next() {
      myChange = myChange.link;
    }

    @Override
    public int getStart1() {
      return myChange.line0;
    }

    @Override
    public int getStart2() {
      return myChange.line1;
    }

    @Override
    public int getEnd1() {
      return myChange.line0 + myChange.deleted;
    }

    @Override
    public int getEnd2() {
      return myChange.line1 + myChange.inserted;
    }
  }
}
