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

import com.intellij.diff.util.IntPair;
import com.intellij.diff.util.MergeRange;
import com.intellij.diff.util.Range;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isWhiteSpace;

@SuppressWarnings({"Duplicates", "unused", "TypeParameterExplicitlyExtendsObject"})
public class TrimUtil {
  public static boolean isPunctuation(char c) {
    if (c == '_') return false;
    boolean isPunctuation = false;
    isPunctuation |= c >= 33 & c <= 47; // !"#$%&'()*+,-./
    isPunctuation |= c >= 58 & c <= 64; // :;<=>?@
    isPunctuation |= c >= 91 & c <= 96; // [\]^_`
    isPunctuation |= c >= 123 & c <= 126; // {|}~
    return isPunctuation;
  }

  public static boolean isAlpha(char c) {
    return !isWhiteSpace(c) && !isPunctuation(c);
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
  public static MergeRange trim(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull CharSequence text3,
                                      int start1, int start2, int start3, int end1, int end2, int end3) {
    start1 = trimStart(text1, start1, end1);
    end1 = trimEnd(text1, start1, end1);
    start2 = trimStart(text2, start2, end2);
    end2 = trimEnd(text2, start2, end2);
    start3 = trimStart(text3, start3, end3);
    end3 = trimEnd(text3, start3, end3);

    return new MergeRange(start1, end1, start2, end2, start3, end3);
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
  public static Range expand(@NotNull List<?> text1, @NotNull List<?> text2,
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

  public static int expandForward(@NotNull List<?> text1, @NotNull List<?> text2,
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

  public static int expandBackward(@NotNull List<?> text1, @NotNull List<?> text2,
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

  public static int expandForwardW(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull CharSequence text3,
                                   int start1, int start2, int start3, int end1, int end2, int end3) {
    int oldStart1 = start1;
    while (start1 < end1 && start2 < end2 && start3 < end3) {
      char c1 = text1.charAt(start1);
      char c2 = text2.charAt(start2);
      char c3 = text3.charAt(start3);
      if (c1 != c2 || c1 != c3 || !isWhiteSpace(c1)) break;
      start1++;
      start2++;
      start3++;
    }

    return start1 - oldStart1;
  }

  public static int expandBackwardW(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull CharSequence text3,
                                    int start1, int start2, int start3, int end1, int end2, int end3) {
    int oldEnd1 = end1;
    while (start1 < end1 && start2 < end2 && start3 < end3) {
      char c1 = text1.charAt(end1 - 1);
      char c2 = text2.charAt(end2 - 1);
      char c3 = text3.charAt(end3 - 1);
      if (c1 != c2 || c1 != c3|| !isWhiteSpace(c1)) break;
      end1--;
      end2--;
      end3--;
    }

    return oldEnd1 - end1;
  }

  @NotNull
  public static IntPair expandForwardIW(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                        int start1, int start2, int end1, int end2) {
    while (start1 < end1 && start2 < end2) {
      char c1 = text1.charAt(start1);
      char c2 = text2.charAt(start2);

      if (c1 == c2) {
        start1++;
        start2++;
        continue;
      }

      boolean skipped = false;
      if (isWhiteSpace(c1)) {
        skipped = true;
        start1++;
      }
      if (isWhiteSpace(c2)) {
        skipped = true;
        start2++;
      }
      if (!skipped) break;
    }

    start1 = trimStart(text1, start1, end1);
    start2 = trimStart(text2, start2, end2);

    return new IntPair(start1, start2);
  }

  @NotNull
  public static IntPair expandBackwardIW(@NotNull CharSequence text1, @NotNull CharSequence text2,
                                         int start1, int start2, int end1, int end2) {
    while (start1 < end1 && start2 < end2) {
      char c1 = text1.charAt(end1 - 1);
      char c2 = text2.charAt(end2 - 1);

      if (c1 == c2) {
        end1--;
        end2--;
        continue;
      }

      boolean skipped = false;
      if (isWhiteSpace(c1)) {
        skipped = true;
        end1--;
      }
      if (isWhiteSpace(c2)) {
        skipped = true;
        end2--;
      }
      if (!skipped) break;
    }

    end1 = trimEnd(text1, start1, end1);
    end2 = trimEnd(text2, start2, end2);

    return new IntPair(end1, end2);
  }

  @NotNull
  public static Range expandIW(@NotNull CharSequence text1, @NotNull CharSequence text2,
                               int start1, int start2, int end1, int end2) {
    IntPair start = expandForwardIW(text1, text2, start1, start2, end1, end2);
    start1 = start.val1;
    start2 = start.val2;

    IntPair end = expandBackwardIW(text1, text2, start1, start2, end1, end2);
    end1 = end.val1;
    end2 = end.val2;

    return new Range(start1, end1, start2, end2);
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

  @NotNull
  public static MergeRange trim(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull CharSequence text3,
                                @NotNull MergeRange range) {
    return trim(text1, text2, text3, range.start1, range.start2, range.start3, range.end1, range.end2, range.end3);
  }

  @NotNull
  public static Range expandIW(@NotNull CharSequence text1, @NotNull CharSequence text2) {
    return expandIW(text1, text2, 0, 0, text1.length(), text2.length());
  }
}
