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

import java.util.Collection;
import java.util.Iterator;

class DiffFragmentsDiffIterable extends ChangeDiffIterableBase {
  @NotNull private final Collection<? extends DiffFragment> myFragments;

  DiffFragmentsDiffIterable(@NotNull Collection<? extends DiffFragment> ranges, int length1, int length2) {
    super(length1, length2);
    myFragments = ranges;
  }

  @NotNull
  @Override
  protected ChangeIterable createChangeIterable() {
    return new FragmentsChangeIterable(myFragments);
  }

  private static class FragmentsChangeIterable implements ChangeIterable {
    private final Iterator<? extends DiffFragment> myIterator;
    private DiffFragment myLast;

    private FragmentsChangeIterable(@NotNull Collection<? extends DiffFragment> fragments) {
      myIterator = fragments.iterator();

      next();
    }

    @Override
    public boolean valid() {
      return myLast != null;
    }

    @Override
    public void next() {
      myLast = myIterator.hasNext() ? myIterator.next() : null;
    }

    @Override
    public int getStart1() {
      return myLast.getStartOffset1();
    }

    @Override
    public int getStart2() {
      return myLast.getStartOffset2();
    }

    @Override
    public int getEnd1() {
      return myLast.getEndOffset1();
    }

    @Override
    public int getEnd2() {
      return myLast.getEndOffset2();
    }
  }
}
