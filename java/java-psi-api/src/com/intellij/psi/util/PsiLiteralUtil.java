// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.util;

import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PsiLiteralUtil {
  @NonNls public static final String HEX_PREFIX = "0x";
  @NonNls public static final String BIN_PREFIX = "0b";
  @NonNls public static final String _2_IN_31 = Long.toString(-1L << 31).substring(1);
  @NonNls public static final String _2_IN_63 = Long.toString(-1L << 63).substring(1);

  private static final String QUOT = "&quot;";

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
   * Convert a string that contains a character (e.g. ""\n"" or ""\\"", etc.)
   * to a character literal string (e.g. "'\n'" or "'\\'", etc.)
   *
   * @param text a string to convert
   * @return the converted string
   */
  @NotNull
  public static String charLiteralForCharString(@NotNull final String text) {
    final int length = text.length();
    if (length <= 1) return text;

    final String character = text.substring(1, length - 1);
    final String charLiteral;
    if ("'".equals(character)) {
      charLiteral = "'\\''";
    }
    else if ("\\\"".equals(character)) {
      charLiteral = "'\"'";
    }
    else {
      charLiteral = '\'' + character + '\'';
    }
    return charLiteral;
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
   * This is required since spaces in the end of the line are trimmed by default (see JEP 368).
   * If escapeSpacesInTheEnd is set, then all spaces before the end of the line are converted even if new line in the end is missing. </li>
   * <li> All new line escape sequences are interpreted. </li>
   * <li>Rest of the content is processed as is.</li>
   *
   * @param s                    original text
   * @param escapeStartQuote     true if first quote should be escaped (e.g. when copy-pasting into text block after two quotes)
   * @param escapeEndQuote       true if last quote should be escaped (e.g. inserting text into text block before closing quotes)
   * @param escapeSpacesInTheEnd true if spaces in the end of the line should be preserved even if no new line in the end is present
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
      result.append(StringUtil.repeat(" ", nSpaces - 1)).append("\\s");
      return i;
    }
    int nextIdx = i >= s.length() ? -1 : parseBackSlash(s, i);
    if (nextIdx != -1 && nextIdx < s.length() && s.charAt(nextIdx) == 'n') {
      result.append(StringUtil.repeat(" ", nSpaces - 1)).append("\\s");
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
   * Parse backslash at given index. It will be parsed even in case when backslash is represented as unicode escape sequence.
   *
   * @param str text
   * @param idx parse from
   * @return index where next char starts, -1 otherwise
   */
  public static int parseBackSlash(@NotNull String str, int idx) {
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

  /**
   * Returns the lines of text inside the quotes of a text block. No further processing is performed.
   * Any escaped characters will remain escaped. Indent is not stripped.
   *
   * @param expression  a text block expression
   * @return the lines of the expression, or null if the expression is not a text block.
   */
  public static String @Nullable [] getTextBlockLines(PsiLiteralExpression expression) {
    if (!expression.isTextBlock()) return null;
    String rawText = expression.getText();
    if (rawText.length() < 7 || !rawText.endsWith("\"\"\"")) return null;
    int start = 3;
    while (true) {
      char c = rawText.charAt(start++);
      if (c == '\n') break;
      if (!Character.isWhitespace(c) || start == rawText.length()) return null;
    }
    return rawText.substring(start, rawText.length() - 3).split("\n", -1);
  }

  /**
   * Determines how many whitespaces would be excluded at the beginning of each line of text block content.
   * See JEP 368 for more details.
   *
   * @see #getTextBlockIndent(String[], boolean, boolean)
   * @param expression a text block literal expression
   * @return the indent of the text block counted in characters, where a tab is also counted as 1.
   */
  public static int getTextBlockIndent(PsiLiteralExpression expression) {
    String[] lines = getTextBlockLines(expression);
    if (lines == null) return -1;
    return getTextBlockIndent(lines);
  }

  /**
   * @see #getTextBlockIndent(PsiLiteralExpression)
   */
  public static int getTextBlockIndent(String @NotNull [] lines) {
    return getTextBlockIndent(lines, false, false);
  }

  /**
   * @see #getTextBlockIndent(PsiLiteralExpression)
   */
  public static int getTextBlockIndent(String @NotNull [] lines, boolean preserveContent, boolean ignoreLastLine) {
    int prefix = Integer.MAX_VALUE;
    for (int i = 0; i < lines.length && prefix != 0; i++) {
      String line = lines[i];
      int indent = 0;
      while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) indent++;
      if (indent == line.length() && (i < lines.length - 1 || ignoreLastLine)) {
        if (!preserveContent) lines[i] = "";
      }
      else if (indent < prefix) prefix = indent;
    }
    return prefix;
  }

  /**
   * Returns the text inside the quotes of a regular string literal. No further processing is performed.
   * Any escaped characters will remain escaped.
   *
   * @param expression  regular string literal.
   * @return the text inside the quotes, or null if the expression is not a string literal.
   */
  @Nullable
  public static String getStringLiteralContent(PsiLiteralExpression expression) {
    String text = expression.getText();
    int textLength = text.length();
    if (textLength > 1 && text.charAt(0) == '\"' && text.charAt(textLength - 1) == '\"') {
      return text.substring(1, textLength - 1);
    }
    if (textLength > QUOT.length() && text.startsWith(QUOT) && text.endsWith(QUOT)) {
      return text.substring(QUOT.length(), textLength - QUOT.length());
    }
    return null;
  }

  /**
   * Return the text of the specified text block without indent and trailing whitespace.
   * Any escaped character will remain escaped.
   *
   * @param expression  a text block expression
   * @return the text of the text block, or null if the expression is not a text block.
   */
  @Nullable
  public static String getTextBlockText(PsiLiteralExpression expression) {
    String[] lines = getTextBlockLines(expression);
    if (lines == null) return null;

    int prefix = getTextBlockIndent(lines);

    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.length() > 0) {
        sb.append(trimTrailingWhitespace(line.substring(prefix)));
      }
      if (i < lines.length - 1) {
        sb.append('\n');
      }
    }
    return sb.toString();
  }

  @NotNull
  private static String trimTrailingWhitespace(@NotNull String line) {
    int index = line.length() - 1;
    while (index >= 0 && Character.isWhitespace(line.charAt(index))) index--;
    if (index >= 0 && index < line.length() - 1 && line.charAt(index) == '\\') index++;
    return line.substring(0, index + 1);
  }

  /**
   * Maps the substring range inside Java String literal value back into the source code range.
   *
   * @param text string literal as present in source code (including quotes)
   * @param from start offset inside the represented string
   * @param to end offset inside the represented string
   * @return the range which represents the corresponding substring inside source representation,
   * or null if from/to values are out of bounds.
   */
  @Nullable
  public static TextRange mapBackStringRange(@NotNull String text, int from, int to) {
    if (from > to || to < 0) return null;
    if (text.length() < 2 || !text.startsWith("\"") || !text.endsWith("\"")) {
      return null;
    }
    if (text.indexOf('\\') == -1) {
      return new TextRange(from + 1, to + 1);
    }
    text = text.substring(1, text.length() - 1);
    int charsSoFar = 0;
    int mappedFrom = -1;
    for (int i = 0; i != -1; i = getCharEndIndex(text, i)) {
      if (charsSoFar == from) {
        mappedFrom = i;
      }
      if (charsSoFar == to) {
        // +1 to count open quote
        return new TextRange(mappedFrom + 1, i + 1);
      }
      charsSoFar++;
    }
    return null;
  }

  /**
   * Maps the substring range inside Java Text Block literal value back into the source code range.
   *
   * @param indent text block indent
   * @return range in source code representation, null when from/to out of bounds or given text block source code representation is invalid
   */
  @Nullable
  public static TextRange mapBackTextBlockRange(@NotNull String text, int from, int to, int indent) {
    if (from > to || to < 0) return null;
    TextBlockModel model = TextBlockModel.create(text, indent);
    if (model == null) return null;
    return model.mapTextBlockRangeBack(from, to);
  }

  private static int getCharEndIndex(@NotNull String line, int i) {
    if (i >= line.length()) return -1;
    char c = line.charAt(i++);
    if (c == '\\') {
      // like \u0020
      char c1 = line.charAt(i++);
      if (c1 == 'u') {
        while (i < line.length() && line.charAt(i) == 'u') i++;
        i += 4;
      } else if (c1 >= '0' && c1 <= '7') { // octal escape
        char c2 = i < line.length() ? line.charAt(i) : 0;
        if (c2 >= '0' && c2 <= '7') {
          i++;
          char c3 = i < line.length() ? line.charAt(i) : 0;
          if (c3 >= '0' && c3 <= '7' && c1 <= '3') {
            i++;
          }
        }
      }
    }
    return i;
  }

  /**
   * @param literal numeric literal to convert
   * @param wantedType wanted type
   * @return textual representation of converted numeric literal; null if not supported
   * or conversion overflows, or supplied literal is not a numeric literal,
   * or wanted type is not a numeric type.
   */
  public static @Nullable String tryConvertNumericLiteral(@NotNull PsiLiteralExpression literal, PsiType wantedType) {
    PsiType exprType = literal.getType();
    if (PsiType.INT.equals(exprType)) {
      if (PsiType.LONG.equals(wantedType)) {
        return literal.getText() + "L";
      }
      if (PsiType.FLOAT.equals(wantedType)) {
        String text = literal.getText();
        if (!text.startsWith("0")) {
          return text + "F";
        }
      }
      if (PsiType.DOUBLE.equals(wantedType)) {
        String text = literal.getText();
        if (!text.startsWith("0")) {
          return text + ".0";
        }
      }
    }
    if (PsiType.LONG.equals(exprType) && PsiType.INT.equals(wantedType)) {
      Long value = ObjectUtils.tryCast(literal.getValue(), Long.class);
      if (value != null && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
        String text = literal.getText();
        if (StringUtil.endsWithIgnoreCase(text, "L")) {
          return text.substring(0, text.length() - 1);
        }
      }
    }
    if (PsiType.DOUBLE.equals(exprType) && PsiType.FLOAT.equals(wantedType)) {
      Double value = ObjectUtils.tryCast(literal.getValue(), Double.class);
      if (value != null && (double)(float)(double)value == value) {
        String text = literal.getText();
        if (StringUtil.endsWithIgnoreCase(text, "D")) {
          text = text.substring(0, text.length() - 1);
        }
        return text + "F";
      }
    }
    if (PsiType.FLOAT.equals(exprType) && PsiType.DOUBLE.equals(wantedType)) {
      String text = literal.getText();
      if (StringUtil.endsWithIgnoreCase(text, "F")) {
        String newLiteral = text.substring(0, text.length() - 1);
        if (!StringUtil.containsAnyChar(newLiteral, ".eEpP")) {
          newLiteral += ".0";
        }
        return newLiteral;
      }
    }
    return null;
  }

  private static final class TextBlockModel {

    private final String[] lines;
    private final int indent;
    private final int startPrefixLength;

    private TextBlockModel(String[] lines, int indent, int startPrefixLength) {
      this.lines = lines;
      this.indent = indent;
      this.startPrefixLength = startPrefixLength;
    }

    @Nullable
    private TextRange mapTextBlockRangeBack(int from, int to) {
      int curOffset = startPrefixLength;
      int charsSoFar = 0;
      int mappedFrom = -1;
      for (int i = 0; i < lines.length; i++) {
        String line = lines[i];
        int linePrefixLength = findLinePrefixLength(line, indent);
        line = line.substring(linePrefixLength);
        boolean isLastLine = i == lines.length - 1;
        int lineSuffixLength = findLineSuffixLength(line, isLastLine);
        line = line.substring(0, line.length() - lineSuffixLength);
        if (!isLastLine) line += '\n';

        curOffset += linePrefixLength;

        int charIdx;
        int nextIdx = 0;
        while (true) {
          if (from == charsSoFar) {
            mappedFrom = curOffset + nextIdx;
          }
          if (to == charsSoFar) {
            return new TextRange(mappedFrom, curOffset + nextIdx);
          }
          charIdx = nextIdx;
          nextIdx = getCharEndIndex(line, charIdx);
          if (nextIdx == -1) break;
          charsSoFar++;
          if (nextIdx == line.length()) curOffset += lineSuffixLength;
        }
        curOffset += line.length();
      }
      return null;
    }

    private static int findLinePrefixLength(@NotNull String line, int indent) {
      boolean isBlankLine = line.chars().allMatch(Character::isWhitespace);
      return isBlankLine ? line.length() : indent;
    }

    private static int findLineSuffixLength(@NotNull String line, boolean isLastLine) {
      if (isLastLine) return 0;
      int lastIdx = line.length() - 1;
      for (int i = lastIdx; i >= 0; i--) if (!Character.isWhitespace(line.charAt(i))) return lastIdx - i;
      return 0;
    }

    @Nullable
    private static TextBlockModel create(@NotNull String text, int indent) {
      if (text.length() < 7 || !text.startsWith("\"\"\"") || !text.endsWith("\"\"\"")) return null;
      int startPrefixLength = findStartPrefixLength(text);
      if (startPrefixLength == -1) return null;
      String[] lines = text.substring(startPrefixLength, text.length() - 3).split("\n", -1);
      return new TextBlockModel(lines, indent, startPrefixLength);
    }

    @Contract(pure = true)
    private static int findStartPrefixLength(@NotNull String text) {
      int lineBreakIdx = text.indexOf("\n");
      if (lineBreakIdx == -1) return -1;
      return lineBreakIdx + 1;
    }
  }
}