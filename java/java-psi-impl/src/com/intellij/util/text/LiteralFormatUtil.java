// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.text;

import com.intellij.openapi.util.text.CharFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class LiteralFormatUtil {
  private static final CharFilter UNDERSCORES_FILTER = ch -> ch != '_';

  private LiteralFormatUtil() { }

  @NotNull
  public static String removeUnderscores(@NotNull final String text) {
    return StringUtil.strip(text, UNDERSCORES_FILTER);
  }

  @NotNull
  public static String format(@NotNull final String original, @Nullable final PsiType type) {
    final boolean isFP = PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type);

    String text = original;
    String prefix = "";
    String suffix = "";
    int groupSize = 3;  // dec, oct

    if (text.startsWith("0x") || text.startsWith("0X") ||
        text.startsWith("0b") || text.startsWith("0B")) {
      prefix = text.substring(0, 2);
      text = text.substring(2);
      groupSize = 4;  // hex, bin
    }

    if (text.length() == 0) return original;

    final char last = text.charAt(text.length() - 1);
    if (StringUtil.containsChar("Ll", last) ||
        (isFP && StringUtil.containsChar("FfDd", last))) {
      final int pos = text.length() - 1;
      suffix = text.substring(pos);
      text = text.substring(0, pos);
    }

    if (text.length() == 0) return original;

    boolean hasPoint = false;
    String fractional = "";
    String exponentMark = "";
    String exponent = "";
    if (isFP) {
      int pos = StringUtil.indexOfAny(text, ("0x".equals(prefix) || "0X".equals(prefix) ? "Pp" : "Ee"));
      if (pos >= 0) {
        int pos2 = Math.max(StringUtil.indexOfAny(text, "+-", pos, text.length()), pos) + 1;
        exponentMark = text.substring(pos, pos2);
        exponent = text.substring(pos2);
        text = text.substring(0, pos);
      }

      pos = text.indexOf('.');
      if (pos >= 0) {
        hasPoint = true;
        fractional = text.substring(pos + 1);
        text = text.substring(0, pos);
      }
    }

    final StringBuilder buffer = new StringBuilder();
    buffer.append(prefix);
    appendFromEnd(buffer, text, groupSize);
    if (isFP) {
      if (hasPoint) buffer.append('.');
      appendFromStart(buffer, fractional, groupSize);
      buffer.append(exponentMark);
      appendFromEnd(buffer, exponent, 3);  // exponent is always decimal
    }
    buffer.append(suffix);
    return buffer.toString();
  }

  private static void appendFromEnd(final StringBuilder buffer, final String original, final int groupSize) {
    final int position = buffer.length();
    int pointer = original.length();
    while (pointer > groupSize) {
      buffer.insert(position, original.substring(pointer - groupSize, pointer));
      buffer.insert(position, '_');
      pointer -= groupSize;
    }

    if (pointer > 0) {
      buffer.insert(position, original.substring(0, pointer));
    }
  }

  private static void appendFromStart(final StringBuilder buffer, final String original, final int groupSize) {
    int pointer = 0;
    while (pointer + groupSize < original.length()) {
      buffer.append(original, pointer, pointer + groupSize);
      buffer.append('_');
      pointer += groupSize;
    }

    if (pointer < original.length()) {
      buffer.append(original.substring(pointer));
    }
  }
}
