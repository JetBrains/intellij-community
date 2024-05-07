// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BasicLiteralUtil {
  private BasicLiteralUtil() {
  }

  /**
   * Returns the indent string for a text block expression. The indent string is calculated based on the indentation
   * of the text block lines.
   *
   * @param expression the text block expression to calculate indent for
   * @return The indent string of the text block, or null if it is impossible to determine indent string.
   */
  public static @Nullable String getTextBlockIndentString(@NotNull PsiElement expression) {
    String[] lines = getTextBlockLines(expression.getText(), true);
    if (lines == null) return null;
    return getTextBlockIndentString(lines);
  }

  /**
   * @param expression text block expression to calculate indent for
   * @return the indent of text block lines; may return -1 if text block is heavily malformed
   */
  public static int getTextBlockIndent(@NotNull PsiElement expression) {
    String[] lines = getTextBlockLines(expression.getText(), true);
    if (lines == null) return -1;
    return getTextBlockIndent(lines);
  }

  /**
   * Returns the lines of text inside the quotes of a text block. No further processing is performed.
   * Any escaped characters will remain escaped. Indent is not stripped.
   *
   * @param expression a text block expression
   * @return the lines of the expression, or null if the expression is not a text block.
   */
  public static String @Nullable [] getTextBlockLines(@NotNull PsiElement expression) {
    String rawText = expression.getText();
    return getTextBlockLines(rawText);
  }

  /**
   * @param rawText text block text, including triple quotes at the start and at the end
   * @return array of textblock content lines, including indent; null if text block is malformed
   */
  public static String @Nullable [] getTextBlockLines(String rawText) {
    return getTextBlockLines(rawText, false);
  }

  /**
   * @param rawText text block text, including triple quotes at the start and at the end
   * @param skipFirstLine if true, skip invalid content in the first line after triple quotes
   * @return array of textblock content lines, including indent; null if text block is malformed
   */
  private static String @Nullable [] getTextBlockLines(String rawText, boolean skipFirstLine) {
    if (rawText.length() < 7 || !rawText.startsWith("\"\"\"") || !rawText.endsWith("\"\"\"")) return null;
    int start = 3;
    while (true) {
      char c = rawText.charAt(start++);
      if (c == '\n') break;
      if (!skipFirstLine && !isTextBlockWhiteSpace(c) || start == rawText.length()) return null;
    }
    return rawText.substring(start, rawText.length() - 3).split("\n", -1);
  }

  public static boolean isTextBlockWhiteSpace(char c) {
    return c == ' ' || c == '\t' || c == '\f';
  }


  public static int getTextBlockIndent(String @NotNull [] lines) {
    return getTextBlockIndent(lines, false, false);
  }

  public static int getTextBlockIndent(String @NotNull [] lines, boolean preserveContent, boolean ignoreLastLine) {
    return getTextBlockIndentInner(lines, preserveContent, ignoreLastLine).indentSize;
  }

  private static @NotNull IndentResult getTextBlockIndentInner(String @NotNull[] lines, boolean preserveContent, boolean ignoreLastLine) {
    int prefix = Integer.MAX_VALUE;
    int position = -1;
    for (int i = 0; i < lines.length && prefix != 0; i++) {
      String line = lines[i];
      int indent = 0;
      while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) indent++;
      if (indent == line.length() && (i < lines.length - 1 || ignoreLastLine)) {
        if (!preserveContent) lines[i] = "";
        if (lines.length == 1) {
          prefix = indent;
          position = i;
        }
      }
      else if (indent < prefix) {
        prefix = indent;
        position = i;
      }
    }
    return new IndentResult(prefix, position);
  }

  private static @NotNull String getTextBlockIndentString(String @NotNull [] lines) {
    IndentResult result = getTextBlockIndentInner(lines, true, false);
    String restorationLine = lines[result.indentLineNumber];
    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < result.indentSize; ++i) {
      builder.append(restorationLine.charAt(i));
    }
    return builder.toString();
  }

  private static class IndentResult {
    private final int indentSize;
    private final int indentLineNumber;

    private IndentResult(int indentSize, int indentLineNumber) {
      this.indentSize = indentSize;
      this.indentLineNumber = indentLineNumber;
    }
  }
}
