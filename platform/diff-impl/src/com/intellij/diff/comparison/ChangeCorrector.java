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
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.ByChar.Char;
import com.intellij.diff.comparison.ByLine.Line;
import com.intellij.diff.comparison.ByLine.LineWrapper;
import com.intellij.diff.comparison.iterables.DiffIterableUtil;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.diff.comparison.TrimUtil.expand;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.diff;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.fair;

/*
 * Base class for two-step diff algorithms.
 * Given matching between some sub-sequences of base sequences - build matching on whole base sequences
 */
abstract class ChangeCorrector {
  // TODO: use additional list with original indexes instead of interface ?
  // TODO: we can use information about elements, that could not be matched (because they was not matched during first step)

  public interface CorrectableData {
    int getOriginalIndex();
  }

  @NotNull private final List<? extends CorrectableData> myData1;
  @NotNull private final List<? extends CorrectableData> myData2;
  private final int myLength1;
  private final int myLength2;
  @NotNull private final FairDiffIterable myChanges;

  @NotNull protected final ProgressIndicator myIndicator;

  @NotNull protected final DiffIterableUtil.ChangeBuilder myBuilder;

  public ChangeCorrector(@NotNull List<? extends CorrectableData> data1,
                         @NotNull List<? extends CorrectableData> data2,
                         int length1,
                         int length2,
                         @NotNull FairDiffIterable changes,
                         @NotNull ProgressIndicator indicator) {
    myData1 = data1;
    myData2 = data2;
    myLength1 = length1;
    myLength2 = length2;
    myChanges = changes;
    myIndicator = indicator;

    myBuilder = new DiffIterableUtil.ChangeBuilder(length1, length2);
  }

  public FairDiffIterable build() {
    execute();
    return fair(myBuilder.finish());
  }

  private int offset1 = 0;
  private int offset2 = 0;

  protected void execute() {
    for (Range ch : myChanges.iterateUnchanged()) {
      int count = ch.end1 - ch.start1;
      for (int i = 0; i < count; i++) {
        CorrectableData data1 = myData1.get(ch.start1 + i);
        CorrectableData data2 = myData2.get(ch.start2 + i);
        matchedPair(data1.getOriginalIndex(), data2.getOriginalIndex());
      }
    }
    matchGap(offset1, myLength1, offset2, myLength2);
  }


  private void matchedPair(int off1, int off2) {
    matchGap(offset1, off1, offset2, off2);
    myBuilder.markEqual(off1, off2);
    offset1 = off1 + 1;
    offset2 = off2 + 1;
  }

  // match elements in range [start1 - end1) -> [start2 - end2)
  protected abstract void matchGap(int start1, int end1, int start2, int end2);

  //
  // Implementations
  //

  public static class DefaultCharChangeCorrector extends ChangeCorrector {
    @NotNull private final CharSequence myText1;
    @NotNull private final CharSequence myText2;

    public DefaultCharChangeCorrector(@NotNull List<Char> chars1,
                                      @NotNull List<Char> chars2,
                                      @NotNull CharSequence text1,
                                      @NotNull CharSequence text2,
                                      @NotNull FairDiffIterable changes,
                                      @NotNull ProgressIndicator indicator) {
      super(chars1, chars2, text1.length(), text2.length(), changes, indicator);
      myText1 = text1;
      myText2 = text2;
    }

    protected void matchGap(int start1, int end1, int start2, int end2) {
      Range expand = expand(myText1, myText2, start1, start2, end1, end2);

      CharSequence inner1 = myText1.subSequence(expand.start1, expand.end1);
      CharSequence inner2 = myText2.subSequence(expand.start2, expand.end2);
      FairDiffIterable innerChanges = ByChar.compare(inner1, inner2, myIndicator);

      myBuilder.markEqual(start1, start2, expand.start1, expand.start2);

      for (Range chunk : innerChanges.iterateUnchanged()) {
        myBuilder.markEqual(expand.start1 + chunk.start1, expand.start2 + chunk.start2, chunk.end1 - chunk.start1);
      }

      myBuilder.markEqual(expand.end1, expand.end2, end1, end2);
    }
  }

  public static class SmartLineChangeCorrector extends ChangeCorrector {
    @NotNull private final List<Line> myLines1;
    @NotNull private final List<Line> myLines2;

    public SmartLineChangeCorrector(@NotNull List<LineWrapper> newLines1,
                                    @NotNull List<LineWrapper> newLines2,
                                    @NotNull List<Line> lines1,
                                    @NotNull List<Line> lines2,
                                    @NotNull FairDiffIterable changes,
                                    @NotNull ProgressIndicator indicator) {
      super(newLines1, newLines2, lines1.size(), lines2.size(), changes, indicator);
      myLines1 = lines1;
      myLines2 = lines2;
    }

    protected void matchGap(int start1, int end1, int start2, int end2) {
      Range expand = expand(myLines1, myLines2, start1, start2, end1, end2);

      List<Line> inner1 = myLines1.subList(expand.start1, expand.end1);
      List<Line> inner2 = myLines2.subList(expand.start2, expand.end2);
      FairDiffIterable innerChanges = diff(inner1, inner2, myIndicator);

      myBuilder.markEqual(start1, start2, expand.start1, expand.start2);

      for (Range chunk : innerChanges.iterateUnchanged()) {
        myBuilder.markEqual(expand.start1 + chunk.start1, expand.start2 + chunk.start2, chunk.end1 - chunk.start1);
      }

      myBuilder.markEqual(expand.end1, expand.end2, end1, end2);
    }
  }
}
