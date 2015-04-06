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

import com.intellij.diff.comparison.iterables.DiffIterable;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.comparison.TrimUtil.isPunctuation;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.*;
import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

public class ByChar {
  // TODO: we can use int[] instead of Char, but will it noticeable increase performance ?

  @NotNull
  public static FairDiffIterable compare(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Char> chars1 = getAllChars(text1);
    List<Char> chars2 = getAllChars(text2);

    return diff(chars1, chars2, indicator);
  }

  @NotNull
  public static FairDiffIterable compareTwoStep(@NotNull CharSequence text1,
                                                @NotNull CharSequence text2,
                                                @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Char> chars1 = getNonSpaceChars(text1);
    List<Char> chars2 = getNonSpaceChars(text2);

    FairDiffIterable nonSpaceChanges = diff(chars1, chars2, indicator);
    return matchAdjustmentSpaces(chars1, chars2, text1, text2, nonSpaceChanges, indicator);
  }

  @NotNull
  public static DiffIterable compareIgnoreWhitespaces(@NotNull CharSequence text1,
                                                      @NotNull CharSequence text2,
                                                      @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Char> chars1 = getNonSpaceChars(text1);
    List<Char> chars2 = getNonSpaceChars(text2);

    FairDiffIterable changes = diff(chars1, chars2, indicator);
    return matchAdjustmentSpacesIW(chars1, chars2, text1, text2, changes);
  }

  /*
   * Compare punctuation chars only, all other characters are left unmatched
   */
  @NotNull
  public static FairDiffIterable comparePunctuation(@NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull ProgressIndicator indicator) {
    indicator.checkCanceled();

    List<Char> chars1 = getPunctuationChars(text1);
    List<Char> chars2 = getPunctuationChars(text2);

    FairDiffIterable nonSpaceChanges = diff(chars1, chars2, indicator);
    return transfer(chars1, chars2, text1, text2, nonSpaceChanges, indicator);
  }

  //
  // Impl
  //

  @NotNull
  private static FairDiffIterable transfer(@NotNull final List<Char> chars1,
                                           @NotNull final List<Char> chars2,
                                           @NotNull final CharSequence text1,
                                           @NotNull final CharSequence text2,
                                           @NotNull final FairDiffIterable changes,
                                           @NotNull final ProgressIndicator indicator) {
    ChangeBuilder builder = new ChangeBuilder(text1.length(), text2.length());

    for (Range range : changes.iterateUnchanged()) {
      int count = range.end1 - range.start1;
      for (int i = 0; i < count; i++) {
        int offset1 = chars1.get(range.start1 + i).getOffset();
        int offset2 = chars2.get(range.start2 + i).getOffset();
        builder.markEqual(offset1, offset2);
      }
    }

    return fair(builder.finish());
  }

  /*
   * Given DiffIterable on non-space characters, convert it into DiffIterable on original texts.
   *
   * Idea: run fair diff on all gaps between matched characters
   * (inside these pairs could met non-space characters, but they will be unique and can't be matched)
   */
  @NotNull
  private static FairDiffIterable matchAdjustmentSpaces(@NotNull final List<Char> chars1,
                                                        @NotNull final List<Char> chars2,
                                                        @NotNull final CharSequence text1,
                                                        @NotNull final CharSequence text2,
                                                        @NotNull final FairDiffIterable changes,
                                                        @NotNull final ProgressIndicator indicator) {
    return new ChangeCorrector.DefaultCharChangeCorrector(chars1, chars2, text1, text2, changes, indicator).build();
  }

  /*
   * Given DiffIterable on non-whitespace characters, convert it into DiffIterable on original texts.
   *
   * matched characters: matched non-space characters + all adjustment whitespaces
   */
  @NotNull
  private static DiffIterable matchAdjustmentSpacesIW(@NotNull List<Char> chars1,
                                                      @NotNull List<Char> chars2,
                                                      @NotNull CharSequence text1,
                                                      @NotNull CharSequence text2,
                                                      @NotNull FairDiffIterable changes) {
    final List<Range> ranges = new ArrayList<Range>();

    for (Range ch : changes.iterateChanges()) {
      int startOffset1;
      int endOffset1;
      if (ch.start1 == ch.end1) {
        startOffset1 = endOffset1 = expandForwardW(chars1, chars2, text1, text2, ch, true);
      }
      else {
        startOffset1 = chars1.get(ch.start1).getOffset();
        endOffset1 = chars1.get(ch.end1 - 1).getOffset() + 1;
      }

      int startOffset2;
      int endOffset2;
      if (ch.start2 == ch.end2) {
        startOffset2 = endOffset2 = expandForwardW(chars1, chars2, text1, text2, ch, false);
      }
      else {
        startOffset2 = chars2.get(ch.start2).getOffset();
        endOffset2 = chars2.get(ch.end2 - 1).getOffset() + 1;
      }

      ranges.add(new Range(startOffset1, endOffset1, startOffset2, endOffset2));
    }
    return create(ranges, text1.length(), text2.length());
  }

  /*
   * we need it to correct place of insertion/deletion: we want to match whitespaces, if we can to
   *
   * sample: "x y" -> "x zy", space should be matched instead of being ignored.
   */
  private static int expandForwardW(@NotNull List<Char> chars1,
                                    @NotNull List<Char> chars2,
                                    @NotNull CharSequence text1,
                                    @NotNull CharSequence text2,
                                    @NotNull Range ch,
                                    boolean left) {
    int offset1 = ch.start1 == 0 ? 0 : chars1.get(ch.start1 - 1).getOffset() + 1;
    int offset2 = ch.start2 == 0 ? 0 : chars2.get(ch.start2 - 1).getOffset() + 1;

    int start = left ? offset1 : offset2;

    return start + TrimUtil.expandForwardW(text1, text2, offset1, offset2, text1.length(), text2.length());
  }

  //
  // Misc
  //

  @NotNull
  private static List<Char> getAllChars(@NotNull CharSequence text) {
    List<Char> chars = new ArrayList<Char>(text.length());

    for (int i = 0; i < text.length(); i++) {
      chars.add(new Char(text, i));
    }

    return chars;
  }

  @NotNull
  private static List<Char> getNonSpaceChars(@NotNull CharSequence text) {
    List<Char> lines = new ArrayList<Char>();

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (!isWhiteSpace(c)) {
        lines.add(new Char(text, i));
      }
    }

    return lines;
  }

  @NotNull
  private static List<Char> getPunctuationChars(@NotNull CharSequence text) {
    List<Char> lines = new ArrayList<Char>();

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isPunctuation(c)) {
        lines.add(new Char(text, i));
      }
    }

    return lines;
  }

  //
  // Helpers
  //

  static class Char implements ChangeCorrector.CorrectableData {
    @NotNull private final CharSequence myText;
    private final int myOffset;

    public Char(@NotNull CharSequence text, int offset) {
      myText = text;
      myOffset = offset;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      return getContent() == ((Char)o).getContent();
    }

    @NotNull
    public CharSequence getText() {
      return myText;
    }

    public int getOffset() {
      return myOffset;
    }

    public char getContent() {
      return myText.charAt(myOffset);
    }

    @Override
    public int hashCode() {
      return getContent();
    }

    @Override
    public String toString() {
      return String.valueOf(getContent());
    }

    @Override
    public int getOriginalIndex() {
      return myOffset;
    }
  }
}
