// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison;

import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.util.text.Strings;
import com.intellij.util.diff.DiffConfig;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.diff.comparison.TrimUtil.trimEnd;
import static com.intellij.diff.comparison.TrimUtil.trimStart;

public final class ComparisonUtil {
  public static boolean isEquals(@Nullable CharSequence text1, @Nullable CharSequence text2, @NotNull ComparisonPolicy policy) {
    if (text1 == text2) return true;
    if (text1 == null || text2 == null) return false;

    switch (policy) {
      case DEFAULT:
        return StringUtilRt.equal(text1, text2, true);
      case TRIM_WHITESPACES:
        return Strings.equalsTrimWhitespaces(text1, text2);
      case IGNORE_WHITESPACES:
        return Strings.equalsIgnoreWhitespaces(text1, text2);
      default:
        throw new IllegalArgumentException(policy.toString());
    }
  }

  public static int hashCode(@NotNull CharSequence text, @NotNull ComparisonPolicy policy) {
    switch (policy) {
      case DEFAULT:
        return Strings.stringHashCode(text);
      case TRIM_WHITESPACES:
        int offset1 = trimStart(text, 0, text.length());
        int offset2 = trimEnd(text, offset1, text.length());
        return Strings.stringHashCode(text, offset1, offset2);
      case IGNORE_WHITESPACES:
        return Strings.stringHashCodeIgnoreWhitespaces(text);
      default:
        throw new IllegalArgumentException(policy.name());
    }
  }

  @Contract(pure = true)
  public static boolean isEqualTexts(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy) {
    switch (policy) {
      case DEFAULT:
        return StringUtilRt.equal(text1, text2, true);
      case TRIM_WHITESPACES:
        return equalsTrimWhitespaces(text1, text2);
      case IGNORE_WHITESPACES:
        return Strings.equalsIgnoreWhitespaces(text1, text2);
      default:
        throw new IllegalArgumentException(policy.name());
    }
  }

  /**
   * Method is different from {@link Strings#equalsTrimWhitespaces(CharSequence, CharSequence)}.
   * <p>
   * Here, leading/trailing whitespaces for *inner* lines will be ignored as well.
   * Ex: "\nXY\n" and "\n XY \n" strings are equal, "\nXY\n" and "\nX Y\n" strings are different.
   */
  @Contract(pure = true)
  public static boolean equalsTrimWhitespaces(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    int index1 = 0;
    int index2 = 0;

    while (true) {
      boolean lastLine1 = false;
      boolean lastLine2 = false;

      int end1 = Strings.indexOf(s1, '\n', index1) + 1;
      int end2 = Strings.indexOf(s2, '\n', index2) + 1;
      if (end1 == 0) {
        end1 = s1.length();
        lastLine1 = true;
      }
      if (end2 == 0) {
        end2 = s2.length();
        lastLine2 = true;
      }
      if (lastLine1 ^ lastLine2) return false;

      CharSequence line1 = s1.subSequence(index1, end1);
      CharSequence line2 = s2.subSequence(index2, end2);
      if (!Strings.equalsTrimWhitespaces(line1, line2)) return false;

      index1 = end1;
      index2 = end2;
      if (lastLine1) return true;
    }
  }

  public static int getUnimportantLineCharCount() {
    return DiffConfig.UNIMPORTANT_LINE_CHAR_COUNT;
  }
}
