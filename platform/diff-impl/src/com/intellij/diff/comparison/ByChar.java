// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.comparison;

import com.intellij.diff.comparison.iterables.DiffIterable;
import com.intellij.diff.comparison.iterables.FairDiffIterable;
import com.intellij.diff.util.Range;
import com.intellij.openapi.util.Pair;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.diff.comparison.TrimUtil.isPunctuation;
import static com.intellij.diff.comparison.TrimUtil.isWhiteSpaceCodePoint;
import static com.intellij.diff.comparison.iterables.DiffIterableUtil.*;

public final class ByChar {
  @NotNull
  public static FairDiffIterable compare(@NotNull CharSequence text1,
                                         @NotNull CharSequence text2,
                                         @NotNull CancellationChecker indicator) {
    indicator.checkCanceled();

    int[] codePoints1 = getAllCodePoints(text1);
    int[] codePoints2 = getAllCodePoints(text2);

    FairDiffIterable iterable = diff(codePoints1, codePoints2, indicator);

    int offset1 = 0;
    int offset2 = 0;
    ChangeBuilder builder = new ChangeBuilder(text1.length(), text2.length());
    for (Pair<Range, Boolean> pair : iterateAll(iterable)) {
      Range range = pair.first;
      boolean equals = pair.second;

      int end1 = offset1 + countChars(codePoints1, range.start1, range.end1);
      int end2 = offset2 + countChars(codePoints2, range.start2, range.end2);

      if (equals) {
        builder.markEqual(offset1, offset2, end1, end2);
      }

      offset1 = end1;
      offset2 = end2;
    }
    assert offset1 == text1.length();
    assert offset2 == text2.length();

    return fair(builder.finish());
  }

  @NotNull
  public static FairDiffIterable compareTwoStep(@NotNull CharSequence text1,
                                                @NotNull CharSequence text2,
                                                @NotNull CancellationChecker indicator) {
    indicator.checkCanceled();

    CodePointsOffsets codePoints1 = getNonSpaceCodePoints(text1);
    CodePointsOffsets codePoints2 = getNonSpaceCodePoints(text2);

    FairDiffIterable nonSpaceChanges = diff(codePoints1.codePoints, codePoints2.codePoints, indicator);
    return matchAdjustmentSpaces(codePoints1, codePoints2, text1, text2, nonSpaceChanges, indicator);
  }

  @NotNull
  public static DiffIterable compareTrimWhitespaces(@NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull CancellationChecker indicator) {
    FairDiffIterable iterable = compareTwoStep(text1, text2, indicator);
    return new ByWord.TrimSpacesCorrector(iterable, text1, text2, indicator).build();
  }

  @NotNull
  public static DiffIterable compareIgnoreWhitespaces(@NotNull CharSequence text1,
                                                      @NotNull CharSequence text2,
                                                      @NotNull CancellationChecker indicator) {
    indicator.checkCanceled();

    CodePointsOffsets codePoints1 = getNonSpaceCodePoints(text1);
    CodePointsOffsets codePoints2 = getNonSpaceCodePoints(text2);

    FairDiffIterable changes = diff(codePoints1.codePoints, codePoints2.codePoints, indicator);
    return matchAdjustmentSpacesIW(codePoints1, codePoints2, text1, text2, changes);
  }

  /*
   * Compare punctuation chars only, all other characters are left unmatched
   */
  @NotNull
  public static FairDiffIterable comparePunctuation(@NotNull CharSequence text1,
                                                    @NotNull CharSequence text2,
                                                    @NotNull CancellationChecker indicator) {
    indicator.checkCanceled();

    CodePointsOffsets chars1 = getPunctuationChars(text1);
    CodePointsOffsets chars2 = getPunctuationChars(text2);

    FairDiffIterable nonSpaceChanges = diff(chars1.codePoints, chars2.codePoints, indicator);
    return transferPunctuation(chars1, chars2, text1, text2, nonSpaceChanges, indicator);
  }

  //
  // Impl
  //

  @NotNull
  private static FairDiffIterable transferPunctuation(@NotNull final CodePointsOffsets chars1,
                                                      @NotNull final CodePointsOffsets chars2,
                                                      @NotNull final CharSequence text1,
                                                      @NotNull final CharSequence text2,
                                                      @NotNull final FairDiffIterable changes,
                                                      @NotNull final CancellationChecker indicator) {
    ChangeBuilder builder = new ChangeBuilder(text1.length(), text2.length());

    for (Range range : changes.iterateUnchanged()) {
      int count = range.end1 - range.start1;
      for (int i = 0; i < count; i++) {
        // Punctuation code points are always 1 char
        int offset1 = chars1.offsets[range.start1 + i];
        int offset2 = chars2.offsets[range.start2 + i];
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
  private static FairDiffIterable matchAdjustmentSpaces(@NotNull final CodePointsOffsets codePoints1,
                                                        @NotNull final CodePointsOffsets codePoints2,
                                                        @NotNull final CharSequence text1,
                                                        @NotNull final CharSequence text2,
                                                        @NotNull final FairDiffIterable changes,
                                                        @NotNull final CancellationChecker indicator) {
    return new ChangeCorrector.DefaultCharChangeCorrector(codePoints1, codePoints2, text1, text2, changes, indicator).build();
  }

  /*
   * Given DiffIterable on non-whitespace characters, convert it into DiffIterable on original texts.
   *
   * matched characters: matched non-space characters + all adjustment whitespaces
   */
  @NotNull
  private static DiffIterable matchAdjustmentSpacesIW(@NotNull CodePointsOffsets codePoints1,
                                                      @NotNull CodePointsOffsets codePoints2,
                                                      @NotNull CharSequence text1,
                                                      @NotNull CharSequence text2,
                                                      @NotNull FairDiffIterable changes) {
    final List<Range> ranges = new ArrayList<>();

    for (Range ch : changes.iterateChanges()) {
      int startOffset1;
      int endOffset1;
      if (ch.start1 == ch.end1) {
        startOffset1 = endOffset1 = expandForwardW(codePoints1, codePoints2, text1, text2, ch, true);
      }
      else {
        startOffset1 = codePoints1.charOffset(ch.start1);
        endOffset1 = codePoints1.charOffsetAfter(ch.end1 - 1);
      }

      int startOffset2;
      int endOffset2;
      if (ch.start2 == ch.end2) {
        startOffset2 = endOffset2 = expandForwardW(codePoints1, codePoints2, text1, text2, ch, false);
      }
      else {
        startOffset2 = codePoints2.charOffset(ch.start2);
        endOffset2 = codePoints2.charOffsetAfter(ch.end2 - 1);
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
  private static int expandForwardW(@NotNull CodePointsOffsets codePoints1,
                                    @NotNull CodePointsOffsets codePoints2,
                                    @NotNull CharSequence text1,
                                    @NotNull CharSequence text2,
                                    @NotNull Range ch,
                                    boolean left) {
    int offset1 = ch.start1 == 0 ? 0 : codePoints1.charOffsetAfter(ch.start1 - 1);
    int offset2 = ch.start2 == 0 ? 0 : codePoints2.charOffsetAfter(ch.start2 - 1);

    int start = left ? offset1 : offset2;

    return start + TrimUtil.expandWhitespacesForward(text1, text2, offset1, offset2, text1.length(), text2.length());
  }

  //
  // Misc
  //

  private static int @NotNull [] getAllCodePoints(@NotNull CharSequence text) {
    IntList list = new IntArrayList(text.length());

    int len = text.length();
    int offset = 0;

    while (offset < len) {
      int ch = Character.codePointAt(text, offset);
      int charCount = Character.charCount(ch);

      list.add(ch);

      offset += charCount;
    }

    return list.toIntArray();
  }

  @NotNull
  private static CodePointsOffsets getNonSpaceCodePoints(@NotNull CharSequence text) {
    IntList codePoints = new IntArrayList(text.length());
    IntList offsets = new IntArrayList(text.length());

    int len = text.length();
    int offset = 0;

    while (offset < len) {
      int ch = Character.codePointAt(text, offset);
      int charCount = Character.charCount(ch);

      if (!isWhiteSpaceCodePoint(ch)) {
        codePoints.add(ch);
        offsets.add(offset);
      }

      offset += charCount;
    }

    return new CodePointsOffsets(codePoints.toIntArray(), offsets.toIntArray());
  }

  @NotNull
  private static CodePointsOffsets getPunctuationChars(@NotNull CharSequence text) {
    IntList codePoints = new IntArrayList(text.length());
    IntList offsets = new IntArrayList(text.length());

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (isPunctuation(c)) {
        codePoints.add(c);
        offsets.add(i);
      }
    }

    return new CodePointsOffsets(codePoints.toIntArray(), offsets.toIntArray());
  }

  private static int countChars(int[] codePoints, int start, int end) {
    int count = 0;
    for (int i = start; i < end; i++) {
      count += Character.charCount(codePoints[i]);
    }
    return count;
  }

  static class CodePointsOffsets {
    public final int[] codePoints;
    public final int[] offsets;

    CodePointsOffsets(int[] codePoints, int[] offsets) {
      this.codePoints = codePoints;
      this.offsets = offsets;
    }

    public int charOffset(int index) {
      return offsets[index];
    }

    public int charOffsetAfter(int index) {
      return offsets[index] + Character.charCount(codePoints[index]);
    }
  }
}
