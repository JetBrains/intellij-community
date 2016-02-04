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

import com.intellij.diff.fragments.DiffFragment;
import org.jetbrains.annotations.NotNull;

import java.util.List;

class DiffFragmentsDiffIterable extends ChangeDiffIterableBase {
  @NotNull private final List<? extends DiffFragment> myFragments;

  public DiffFragmentsDiffIterable(@NotNull List<? extends DiffFragment> ranges, int length1, int length2) {
    super(length1, length2);
    myFragments = ranges;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new FragmentsChangeIterable();
  }

  private class FragmentsChangeIterable implements ChangeIterable {
    private int myIndex = 0;

    @Override
    public boolean valid() {
      return myIndex != myFragments.size();
    }

    @Override
    public void next() {
      myIndex++;
    }

    @Override
    public int getStart1() {
      return myFragments.get(myIndex).getStartOffset1();
    }

    @Override
    public int getStart2() {
      return myFragments.get(myIndex).getStartOffset2();
    }

    @Override
    public int getEnd1() {
      return myFragments.get(myIndex).getEndOffset1();
    }

    @Override
    public int getEnd2() {
      return myFragments.get(myIndex).getEndOffset2();
    }
  }
}
