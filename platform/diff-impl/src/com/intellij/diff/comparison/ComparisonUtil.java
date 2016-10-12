/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class ComparisonUtil {
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
}
