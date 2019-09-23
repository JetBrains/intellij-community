// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.tree.IElementType;
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

  /**
   * Converts given string to text block content.
   * String is converted as a last string in a text block.
   *
   * @param s original text
   * @see #escapeTextBlockCharacters(String, boolean, boolean, boolean)
   */
  @NotNull
  public static String escapeTextBlockCharacters(@NotNull String s) {
    return escapeTextBlockCharacters(s, false, true, true);
  }

  /**
   * Converts given string to text block content.
   * <p>During conversion:</p>
   * <li>All escaped quotes are unescaped.</li>
   * <li>Every third quote is escaped. If escapeStartQuote / escapeEndQuote is set then start / end quote is also escaped.</li>
   * <li>All spaces before \n are converted to \040 escape sequence.
   * This is required since spaces in the end of the line are trimmed by default (see JEP 355).
   * If escapeSpacesInTheEnd is set, then all spaces before the end of the line are converted even if new line in the end is missing. </li>
   * <li> All new line escape sequences are interpreted. </li>
   * <li>Rest of the content is processed as is.</li>
   *
   * @param s                    original text
   * @param escapeStartQuote     true if first quote should be escaped (e.g. when copy-pasting into text block after two quotes)
   * @param escapeEndQuote       true if last quote should be escaped (e.g. inserting text into text block before closing quotes)
   * @param escapeSpacesInTheEnd true if spaces in the end of the line should be converted to \040 even if no new line in the end is present
   */
  @NotNull
  public static String escapeTextBlockCharacters(@NotNull String s, boolean escapeStartQuote,
                                                 boolean escapeEndQuote, boolean escapeSpacesInTheEnd) {
    int i = 0;
    int length = s.length();
    StringBuilder result = new StringBuilder(length);
    while (i < length) {
      int nextIdx = parseQuotes(i, s, result, escapeStartQuote, escapeEndQuote);
      if (nextIdx != -1) {
        i = nextIdx;
        continue;
      }
      nextIdx = parseSpaces(i, s, result, escapeSpacesInTheEnd);
      if (nextIdx != -1) {
        i = nextIdx;
        continue;
      }
      nextIdx = parseBackSlashes(i, s, result);
      if (nextIdx != -1) {
        i = nextIdx;
        continue;
      }
      result.append(s.charAt(i));
      i++;
    }
    return result.toString();
  }

  private static int parseQuotes(int start, @NotNull String s, @NotNull StringBuilder result,
                                 boolean escapeStartQuote, boolean escapeEndQuote) {
    char c = s.charAt(start);
    if (c != '"') return -1;
    int nQuotes = 1;
    int i = start;
    while (true) {
      int nextIdx = i + 1 >= s.length() ? -1 : parseBackSlash(s, i + 1);
      if (nextIdx == -1) nextIdx = i + 1;
      if (nextIdx >= s.length() || s.charAt(nextIdx) != '"') break;
      nQuotes++;
      i = nextIdx;
    }
    for (int q = 0; q < nQuotes; q++) {
      if (q == 0 && start == 0 && escapeStartQuote ||
          q % 3 == 2 ||
          q == nQuotes - 1 && i + 1 == s.length() && escapeEndQuote) {
        result.append("\\\"");
      }
      else {
        result.append('"');
      }
    }
    return i + 1;
  }

  private static int parseSpaces(int start, @NotNull String s, @NotNull StringBuilder result, boolean escapeSpacesInTheEnd) {
    char c = s.charAt(start);
    if (c != ' ') return -1;
    int i = start;
    int nSpaces = 0;
    while (i < s.length() && s.charAt(i) == ' ') {
      nSpaces++;
      i++;
    }
    if (i >= s.length() && escapeSpacesInTheEnd) {
      result.append(StringUtil.repeat("\\040", nSpaces));
      return i;
    }
    int nextIdx = i >= s.length() ? -1 : parseBackSlash(s, i);
    if (nextIdx != -1 && nextIdx < s.length() && s.charAt(nextIdx) == 'n') {
      result.append(StringUtil.repeat("\\040", nSpaces));
      return i;
    }
    result.append(StringUtil.repeatSymbol(' ', nSpaces));
    return i;
  }

  private static int parseBackSlashes(int start, @NotNull String s, @NotNull StringBuilder result) {
    int i = parseBackSlash(s, start);
    if (i == -1) return -1;
    int prev = start;
    int nextIdx;
    int nSlashes = 1;
    while (i < s.length()) {
      nextIdx = parseBackSlash(s, i);
      if (nextIdx != -1) {
        result.append(s, prev, i);
        prev = i;
        i = nextIdx;
        nSlashes++;
      }
      else {
        break;
      }
    }
    if (i >= s.length()) {
      // line ends with a backslash
      result.append(s, prev, s.length());
    }
    else if (nSlashes % 2 == 0) {
      // symbol after slashes is not escaped
      result.append(s, prev, i);
    }
    else {
      // found something that is escaped with a backslash
      char next = s.charAt(i);
      if (next == 'n') {
        result.append('\n');
      }
      else if (next == '"') {
        return i;
      }
      else {
        result.append(s, prev, i).append(next);
      }
      return i + 1;
    }
    return i;
  }

  /**
   * Escapes backslashes in a text block (even if they're represented as an escape sequence).
   */
  @NotNull
  public static String escapeBackSlashesInTextBlock(@NotNull String str) {
    int i = 0;
    int length = str.length();
    StringBuilder result = new StringBuilder(length);
    while (i < length) {
      int nextIdx = parseBackSlash(str, i);
      if (nextIdx != -1) {
        result.append("\\\\");
        i = nextIdx;
      }
      else {
        result.append(str.charAt(i));
        i++;
      }
    }
    return result.toString();
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