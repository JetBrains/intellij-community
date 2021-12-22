// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.comparison;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public final class ComparisonUtil {
  private static final int UNIMPORTANT_LINE_CHAR_COUNT = Registry.intValue("diff.unimportant.line.char.count");

  @Contract(pure = true)
  public static boolean isEquals(@NotNull CharSequence text1, @NotNull CharSequence text2, @NotNull ComparisonPolicy policy) {
    switch (policy) {
      case DEFAULT:
        return StringUtil.equals(text1, text2);
      case TRIM_WHITESPACES:
        return equalsTrimWhitespaces(text1, text2);
      case IGNORE_WHITESPACES:
        return StringUtil.equalsIgnoreWhitespaces(text1, text2);
      default:
        throw new IllegalArgumentException(policy.name());
    }
  }

  @Contract(pure = true)
  public static boolean equalsTrimWhitespaces(@NotNull CharSequence s1, @NotNull CharSequence s2) {
    int index1 = 0;
    int index2 = 0;

    while (true) {
      boolean lastLine1 = false;
      boolean lastLine2 = false;

      int end1 = StringUtil.indexOf(s1, '\n', index1) + 1;
      int end2 = StringUtil.indexOf(s2, '\n', index2) + 1;
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
      if (!StringUtil.equalsTrimWhitespaces(line1, line2)) return false;

      index1 = end1;
      index2 = end2;
      if (lastLine1) return true;
    }
  }

  public static int getUnimportantLineCharCount() {
    return UNIMPORTANT_LINE_CHAR_COUNT;
  }
}
