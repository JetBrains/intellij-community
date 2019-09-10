// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Contract;
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
      return parseIntegerNoPrefix(text);
    }
    catch (NumberFormatException e) {
      return null;
    }
  }

  @Nullable
  public static Integer parseIntegerNoPrefix(String text) {
    final long l = Long.parseLong(text, 10);
    if (text.equals(_2_IN_31) || l == (long)(int)l) {
      return Integer.valueOf((int)l);
    }
    else {
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

  /**
   * Returns true if given literal expression is invalid and reusing its text representation
   * in refactorings/quick-fixes may result in parse errors.
   *
   * @param expression a literal expression to check
   * @return true if the literal text cannot be safely used to build refactored expression
   */
  public static boolean isUnsafeLiteral(PsiLiteralExpression expression) {
    PsiElement literal = expression.getFirstChild();
    assert literal instanceof PsiJavaToken : literal;
    IElementType type = ((PsiJavaToken)literal).getTokenType();
    return (type == JavaTokenType.CHARACTER_LITERAL || type == JavaTokenType.STRING_LITERAL) && expression.getValue() == null;
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeTextBlockCharacters(@NotNull String s) {
    return escapeTextBlockCharacters(s, false, true);
  }

  @NotNull
  @Contract(pure = true)
  public static String escapeTextBlockCharacters(@NotNull String s, boolean escapeStartQuote, boolean escapeEndQuote) {
    int length = s.length();
    if (length == 0) return s;
    StringBuilder result = new StringBuilder(length);
    int q = 0;
    for (int i = 0; i < length; i++) {
      char c = s.charAt(i);
      if (c == '"') {
        if (escapeStartQuote && i == 0) result.append('\\');
        q++;
      }
      else {
        appendQuotes(q, result);
        if (c == '\\') result.append('\\');
        result.append(c);
        q = 0;
      }
    }
    appendQuotes(q, result);
    if (escapeEndQuote && result.charAt(result.length() - 1) == '"') {
      result.insert(result.length() - 1, '\\');
    }
    return result.toString();
  }

  private static void appendQuotes(int quotes, StringBuilder result) {
    int q = quotes;
    while (q > 0) {
      if (quotes >= 3) result.append('\\');
      result.append(StringUtil.repeat("\"", Math.min(q, 3)));
      q -= 3;
    }
  }

  /**
   * Replaces all unescaped quotes with escaped ones.
   * If text contains backslash escape sequence it's replaced with a regular backslash.
   * The rest of the symbols are left unchanged.
   */
  @NotNull
  public static String escapeQuotes(@NotNull String str) {
    StringBuilder sb = new StringBuilder(str.length());
    int nSlashes = 0;
    int idx = 0;
    while (idx < str.length()) {
      char c = str.charAt(idx);
      int nextIdx = parseBackSlash(str, idx);
      if (nextIdx > 0) {
        nSlashes++;
      }
      else {
        if (c == '\"' && nSlashes % 2 == 0) {
          sb.append('\\');
        }
        nSlashes = 0;
        nextIdx = idx + 1;
      }
      sb.append(c);
      idx = nextIdx;
    }
    return sb.toString();
  }

  private static int parseBackSlash(@NotNull String str, int idx) {
    char c = str.charAt(idx);
    if (c != '\\') return -1;
    int nextIdx = parseEscapedBackSlash(str, idx);
    return nextIdx > 0 ? nextIdx : idx + 1;
  }

  private static int parseEscapedBackSlash(@NotNull String str, int idx) {
    int next = idx + 1;
    if (next >= str.length() || str.charAt(next) != 'u') return -1;
    while (str.charAt(next) == 'u') {
      next++;
    }
    if (next + 3 >= str.length()) return -1;
    try {
      int code = Integer.parseInt(str.substring(next, next + 4), 16);
      if (code == '\\') return next + 4;
    }
    catch (NumberFormatException ignored) {
    }
    return -1;
  }
}