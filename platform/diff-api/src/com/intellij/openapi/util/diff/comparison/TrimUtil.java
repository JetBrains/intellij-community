package com.intellij.openapi.util.diff.comparison;

import com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil.IntPair;
import com.intellij.openapi.util.diff.comparison.iterables.DiffIterableUtil.Range;
import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

@SuppressWarnings({"Duplicates", "unused", "TypeParameterExplicitlyExtendsObject"})
public class TrimUtil {
  private static final String PUNCTUATION = "(){}[],./?`~!@#$%^&*-=+|\\;:'\"<>";
  private static final TIntHashSet PUNCTUATION_SET = new TIntHashSet();

  static {
    for (char punctuation : PUNCTUATION.toCharArray()) {
      PUNCTUATION_SET.add(punctuation);
    }
  }

  public static boolean isPunctuation(char c) {
    return PUNCTUATION_SET.contains(c);
  }

  public static boolean isAlpha(char c) {
    return !isWhiteSpace(c) && !PUNCTUATION_SET.contains(c);
  }

  //
  // Trim
  //

  @NotNull
  public static Range trim(@NotNull CharSequence text1, @NotNull CharSequence text2,
                           int start1, int start2, int end1, int end2) {
    start1 = trimStart(text1, start1, end1);
    end1 = trimEnd(text1, start1, end1);
    start2 = trimStart(text2, start2, end2);
    end2 = trimEnd(text2, start2, end2);

    return new Range(start1, end1, start2, end2);
  }

  @NotNull
  public static IntPair trim(@NotNull CharSequence text, int start, int end) {
    start = trimStart(text, start, end);
    end = trimEnd(text, start, end);

    return new IntPair(start, end);
  }

  public static int trimStart(@NotNull CharSequence text, int start, int end) {
    while (start < end) {
      char c = text.charAt(start);
      if (!isWhiteSpace(c)) break;
      start++;
    }
    return start;
  }

  public static int trimEnd(@NotNull CharSequence text, int start, int end) {
    while (start < end) {
      char c = text.charAt(end - 1);
      if (!isWhiteSpace(c)) break;
      end--;
    }
    return end;
  }

  //
  // Expand
  //

  @NotNull
  public static Range expand(@NotNull List<? extends Object> text1, @NotNull List<? extends Object> text2,
                             int start1, int start2, int end1, int end2) {
    int count1 = expandForward(text1, text2, start1, start2, end1, end2);
    start1 += count1;
    start2 += count1;

    int count2 = expandBackward(text1, text2, start1, start2, end1, end2);
    end1 -= count2;
    end2 -= count2;

    return new Range(start1, end1, start2, end2);
  }

  @NotNull
  public static Range expand(@NotNull CharSequence text1, @NotNull CharSequence text2,
                             int start1, int start2, int end1, int end2) {
    int count1 = expandForward(text1, text2, start1, start2, end1, end2);
    start1 += count1;
    start2 += count1;

    int count2 = expandBackward(text1, text2, start1, start2, end1, end2);
    end1 -= count2;
    end2 -= count2;

    return new Range(start1, end1, start2, end2);
  }

  @NotNull
  public static Range expandW(@NotNull CharSequence text1, @NotNull CharSequence text2,
                              int start1, int start2, int end1, int end2) {
    int count1 = expandForwardW(text1, text2, start1, start2, end1, end2);
    start1 += count1;
    start2 += count1;

    int count2 = expandBackwardW(text1, text2, start1, start2, end1, end2);
    end1 -= count2;
    end2 -= count2;

    return new Range(start1, end1, start2, end2);
  }

  public static int expandForward(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                  int start1, int start2, int end1, int end2) {
    int oldStart1 = start1;
    while (start1 < end1 && start2 < end2) {
      char c1 = text1.charAt(start1);
      char c2 = text2.charAt(start2);
      if (c1 != c2) break;
      start1++;
      start2++;
    }

    return start1 - oldStart1;
  }

  public static int expandForward(@NotNull List<? extends Object> text1, @NotNull List<? extends Object> text2,
                                  int start1, int start2, int end1, int end2) {
    int oldStart1 = start1;
    while (start1 < end1 && start2 < end2) {
      Object c1 = text1.get(start1);
      Object c2 = text2.get(start2);
      if (!c1.equals(c2)) break;
      start1++;
      start2++;
    }

    return start1 - oldStart1;
  }

  public static int expandForwardW(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                   int start1, int start2, int end1, int end2) {
    int oldStart1 = start1;
    while (start1 < end1 && start2 < end2) {
      char c1 = text1.charAt(start1);
      char c2 = text2.charAt(start2);
      if (c1 != c2 || !isWhiteSpace(c1)) break;
      start1++;
      start2++;
    }

    return start1 - oldStart1;
  }

  public static int expandBackward(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                   int start1, int start2, int end1, int end2) {
    int oldEnd1 = end1;
    while (start1 < end1 && start2 < end2) {
      char c1 = text1.charAt(end1 - 1);
      char c2 = text2.charAt(end2 - 1);
      if (c1 != c2) break;
      end1--;
      end2--;
    }

    return oldEnd1 - end1;
  }

  public static int expandBackward(@NotNull List<? extends Object> text1, @NotNull List<? extends Object> text2,
                                   int start1, int start2, int end1, int end2) {
    int oldEnd1 = end1;
    while (start1 < end1 && start2 < end2) {
      Object c1 = text1.get(end1 - 1);
      Object c2 = text2.get(end2 - 1);
      if (!c1.equals(c2)) break;
      end1--;
      end2--;
    }

    return oldEnd1 - end1;
  }

  public static int expandBackwardW(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                    int start1, int start2, int end1, int end2) {
    int oldEnd1 = end1;
    while (start1 < end1 && start2 < end2) {
      char c1 = text1.charAt(end1 - 1);
      char c2 = text2.charAt(end2 - 1);
      if (c1 != c2 || !isWhiteSpace(c1)) break;
      end1--;
      end2--;
    }

    return oldEnd1 - end1;
  }

  //
  // Misc
  //

  @NotNull
  public static Range expand(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull Range range) {
    return expand(text1, text2, range.start1, range.start2, range.end1, range.end2);
  }

  @NotNull
  public static Range trim(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull Range range) {
    return trim(text1, text2, range.start1, range.start2, range.end1, range.end2);
  }
}
