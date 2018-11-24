// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiLiteralUtil {
  @NonNls public static final String HEX_PREFIX = "0x";
  @NonNls public static final String BIN_PREFIX = "0b";
  @NonNls public static final String _2_IN_31 = Long.toString(-1L << 31).substring(1);
  @NonNls public static final String _2_IN_63 = Long.toString(-1L << 63).substring(1);

  @Nullable
  public static Integer parseInteger(String text) {
    try {
      if (text.startsWith(HEX_PREFIX)) {
        // should fit in 32 bits
        final long value = parseDigits(text.substring(2), 4, 32);
        return Integer.valueOf((int)value);
      }
      if (text.startsWith(BIN_PREFIX)) {
        // should fit in 32 bits
        final long value = parseDigits(text.substring(2), 1, 32);
        return Integer.valueOf((int)value);
      }
      if (StringUtil.startsWithChar(text, '0')) {
        // should fit in 32 bits
        final long value = parseDigits(text, 3, 32);
        return Integer.valueOf((int)value);
      }
      final long l = Long.parseLong(text, 10);
      if (text.equals(_2_IN_31)) return Integer.valueOf((int)l);
      long converted = (int)l;
      return l == converted ? Integer.valueOf((int)l) : null;
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public static Long parseLong(String text) {
    if (StringUtil.endsWithChar(text, 'L') || StringUtil.endsWithChar(text, 'l')) {
      text = text.substring(0, text.length() - 1);
    }
    try {
      if (text.startsWith(HEX_PREFIX)) {
        return parseDigits(text.substring(2), 4, 64);
      }
      if (text.startsWith(BIN_PREFIX)) {
        return parseDigits(text.substring(2), 1, 64);
      }
      if (StringUtil.startsWithChar(text, '0')) {
        // should fit in 64 bits
        return parseDigits(text, 3, 64);
      }
      if (_2_IN_63.equals(text)) return Long.valueOf(-1L << 63);
      return Long.valueOf(text, 10);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public static Float parseFloat(String text) {
    try {
      return Float.valueOf(text);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public static Double parseDouble(String text) {
    try {
      return Double.valueOf(text);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  // convert text to number according to radix specified
  // if number is more than maxBits bits long, throws NumberFormatException
  public static long parseDigits(final String text, final int bitsInRadix, final int maxBits) throws NumberFormatException {
    final int radix = 1 << bitsInRadix;
    final int textLength = text.length();
    if (textLength == 0) {
      throw new NumberFormatException(text);
    }
    long integer = textLength == 1 ? 0 : Long.parseLong(text.substring(0, textLength - 1), radix);
    if ((integer & (-1L << (maxBits - bitsInRadix))) != 0) {
      throw new NumberFormatException(text);
    }
    final int lastDigit = Character.digit(text.charAt(textLength - 1), radix);
    if (lastDigit == -1) {
      throw new NumberFormatException(text);
    }
    integer <<= bitsInRadix;
    integer |= lastDigit;
    return integer;
  }

  /**
   * Converts passed character literal (like 'a') to string literal (like "a").
   *
   * @param charLiteral character literal to convert.
   * @return resulting string literal
   */
  @NotNull
  public static String stringForCharLiteral(@NotNull String charLiteral) {
    if ("'\"'".equals(charLiteral)) {
      return "\"\\\"\"";
    }
    else if ("'\\''".equals(charLiteral)) {
      return "\"'\"";
    }
    else {
      return '\"' + charLiteral.substring(1, charLiteral.length() - 1) +
             '\"';
    }
  }
}