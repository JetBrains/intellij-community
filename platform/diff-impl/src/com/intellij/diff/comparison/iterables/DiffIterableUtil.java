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

import com.intellij.diff.comparison.DiffTooBigException;
import com.intellij.diff.fragments.DiffFragment;
import com.intellij.diff.fragments.DiffFragmentImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class DiffIterableUtil {
  /*
   * Compare two arrays, basing on equals() and hashCode() of it's elements
   */
  @NotNull
  public static <T> FairDiffIterable diff(@NotNull T[] data1, @NotNull T[] data2, @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    try {
      // TODO: use ProgressIndicator inside
      Diff.Change change = Diff.buildChanges(data1, data2);
      return fair(create(change, data1.length, data2.length));
    }
    catch (FilesTooBigForDiffException e) {
      throw new DiffTooBigException();
    }
  }

  /*
   * Compare two lists, basing on equals() and hashCode() of it's elements
   */
  @NotNull
  public static <T> FairDiffIterable diff(@NotNull List<T> objects1, @NotNull List<T> objects2, @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    // TODO: compare lists instead of arrays in Diff
    Object[] data1 = ContainerUtil.toArray((List)objects1, new Object[objects1.size()]);
    Object[] data2 = ContainerUtil.toArray((List)objects2, new Object[objects2.size()]);
    return diff(data1, data2, indicator);
  }

  //
  // Iterable
  //

  @NotNull
  public static DiffIterable create(@Nullable Diff.Change change, int length1, int length2) {
    DiffChangeDiffIterable iterable = new DiffChangeDiffIterable(change, length1, length2);
    verify(iterable);
    return iterable;
  }

  @NotNull
  public static DiffIterable createFragments(@NotNull List<? extends DiffFragment> fragments, int length1, int length2) {
    DiffIterable iterable = new DiffFragmentsDiffIterable(fragments, length1, length2);
    verify(iterable);
    return iterable;
  }

  @NotNull
  public static DiffIterable create(@NotNull List<? extends Range> ranges, int length1, int length2) {
    DiffIterable iterable = new RangesDiffIterable(ranges, length1, length2);
    verify(iterable);
    return iterable;
  }

  @NotNull
  public static DiffIterable createUnchanged(@NotNull List<? extends Range> ranges, int length1, int length2) {
    DiffIterable invert = invert(create(ranges, length1, length2));
    verify(invert);
    return invert;
  }

  @NotNull
  public static DiffIterable invert(@NotNull DiffIterable iterable) {
    DiffIterable wrapper = new InvertedDiffIterableWrapper(iterable);
    verify(wrapper);
    return wrapper;
  }

  @NotNull
  public static FairDiffIterable fair(@NotNull DiffIterable iterable) {
    FairDiffIterable wrapper = new FairDiffIterableWrapper(iterable);
    verifyFair(wrapper);
    return wrapper;
  }

  @NotNull
  public static DiffIterable trim(@NotNull DiffIterable iterable, int start1, int end1, int start2, int end2) {
    return new SubiterableDiffIterable(iterable, start1, end1, start2, end2);
  }

  //
  // Misc
  //

  @NotNull
  public static List<DiffFragment> convertIntoFragments(@NotNull DiffIterable changes) {
    final List<DiffFragment> fragments = new ArrayList<DiffFragment>();
    for (Range ch : changes.iterateChanges()) {
      fragments.add(new DiffFragmentImpl(ch.start1, ch.end1, ch.start2, ch.end2));
    }
    return fragments;
  }

  @NotNull
  public static Iterable<Pair<Range, Boolean>> iterateAll(@NotNull final DiffIterable iterable) {
    return new Iterable<Pair<Range, Boolean>>() {
      @Override
      public Iterator<Pair<Range, Boolean>> iterator() {
        return new Iterator<Pair<Range, Boolean>>() {
          @NotNull private final Iterator<Range> myChanges = iterable.changes();
          @NotNull private final Iterator<Range> myUnchanged = iterable.unchanged();

          @Nullable private Range lastChanged = myChanges.hasNext() ? myChanges.next() : null;
          @Nullable private Range lastUnchanged = myUnchanged.hasNext() ? myUnchanged.next() : null;

          @Override
          public boolean hasNext() {
            return lastChanged != null || lastUnchanged != null;
          }

          @Override
          public Pair<Range, Boolean> next() {
            boolean equals;
            if (lastChanged == null) {
              equals = true;
            }
            else if (lastUnchanged == null) {
              equals = false;
            }
            else {
              equals = lastUnchanged.start1 < lastChanged.start1 || lastUnchanged.start2 < lastChanged.start2;
            }

            if (equals) {
              Range range = lastUnchanged;
              lastUnchanged = myUnchanged.hasNext() ? myUnchanged.next() : null;
              //noinspection ConstantConditions
              return Pair.create(range, true);
            }
            else {
              Range range = lastChanged;
              lastChanged = myChanges.hasNext() ? myChanges.next() : null;
              return Pair.create(range, false);
            }
          }

          @Override
          public void remove() {
            throw new UnsupportedOperationException();
          }
        };
      }
    };
  }

  public static boolean isEmpty(@NotNull Range range) {
    return range.start1 == range.end1 && range.start2 == range.end2;
  }

  //
  // Verification
  //

  private static boolean isVerifyEnabled() {
    return Registry.is("diff.verify.iterable"); // TODO: Leave verification for tests only ?
  }

  public static void verify(@NotNull DiffIterable iterable) {
    if (!isVerifyEnabled()) return;

    verify(iterable.iterateChanges());
    verify(iterable.iterateUnchanged());

    verifyFullCover(iterable);
  }

  public static void verifyFair(@NotNull DiffIterable iterable) {
    if (!isVerifyEnabled()) return;

    verify(iterable);

    for (Range range : iterable.iterateUnchanged()) {
      assert range.end1 - range.start1 == range.end2 - range.start2;
    }
  }

  private static void verify(@NotNull Iterable<Range> iterable) {
    for (Range range : iterable) {
      // verify range
      assert range.start1 <= range.end1;
      assert range.start2 <= range.end2;
      assert range.start1 != range.end1 || range.start2 != range.end2;
    }
  }

  private static void verifyFullCover(@NotNull DiffIterable iterable) {
    int last1 = 0;
    int last2 = 0;
    Boolean lastEquals = null;

    for (Pair<Range, Boolean> pair : iterateAll(iterable)) {
      Range range = pair.first;
      Boolean equal = pair.second;

      assert last1 == range.start1;
      assert last2 == range.start2;
      assert !Comparing.equal(lastEquals, equal);

      last1 = range.end1;
      last2 = range.end2;
      lastEquals = equal;
    }

    assert last1 == iterable.getLength1();
    assert last2 == iterable.getLength2();
  }

  //
  // Helpers
  //

  public static class ChangeBuilder {
    private final int myLength1;
    private final int myLength2;

    @Nullable private Diff.Change myFirstChange;
    @Nullable private Diff.Change myLastChange;

    private int myIndex1 = 0;
    private int myIndex2 = 0;

    public ChangeBuilder(int length1, int length2) {
      myLength1 = length1;
      myLength2 = length2;
    }

    private void addChange(int start1, int start2, int end1, int end2) {
      Diff.Change change = new Diff.Change(start1, start2, end1 - start1, end2 - start2, null);
      if (myLastChange != null) {
        myLastChange.link = change;
      }
      else {
        myFirstChange = change;
      }
      myLastChange = change;
      myIndex1 = end1;
      myIndex2 = end2;
    }

    public void markEqual(int index1, int index2) {
      markEqual(index1, index2, 1);
    }

    public void markEqual(int index1, int index2, int count) {
      markEqual(index1, index2, index1 + count, index2 + count);
    }

    public void markEqual(int index1, int index2, int end1, int end2) {
      if (index1 == end1 && index2 == end2) return;

      assert myIndex1 <= index1;
      assert myIndex2 <= index2;
      assert index1 <= end1;
      assert index2 <= end2;

      if (myIndex1 != index1 || myIndex2 != index2) {
        addChange(myIndex1, myIndex2, index1, index2);
      }
      myIndex1 = end1;
      myIndex2 = end2;
    }

    private void finish(int length1, int length2) {
      assert myIndex1 <= length1;
      assert myIndex2 <= length2;

      if (length1 != myIndex1 || length2 != myIndex2) {
        addChange(myIndex1, myIndex2, length1, length2);
      }
    }

    @NotNull
    public DiffIterable finish() {
      finish(myLength1, myLength2);
      return create(myFirstChange, myLength1, myLength2);
    }
  }

  public static class Range {
    public final int start1;
    public final int end1;
    public final int start2;
    public final int end2;

    public Range(int start1, int end1, int start2, int end2) {
      this.start1 = start1;
      this.end1 = end1;
      this.start2 = start2;
      this.end2 = end2;
    }
  }

  public static class IntPair {
    public final int val1;
    public final int val2;

    public IntPair(int val1, int val2) {
      this.val1 = val1;
      this.val2 = val2;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      IntPair pair = (IntPair)o;

      if (val1 != pair.val1) return false;
      if (val2 != pair.val2) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = val1;
      result = 31 * result + val2;
      return result;
    }
  }
}
