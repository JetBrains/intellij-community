// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class BasicLiteralUtil {
  private BasicLiteralUtil() {
  }

  public static int getTextBlockIndent(@NotNull PsiElement expression) {
    String[] lines = getTextBlockLines(expression);
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


  public static String @Nullable [] getTextBlockLines(String rawText) {
    if (rawText.length() < 7 || !rawText.endsWith("\"\"\"")) return null;
    int start = 3;
    while (true) {
      char c = rawText.charAt(start++);
      if (c == '\n') break;
      if (!isTextBlockWhiteSpace(c) || start == rawText.length()) return null;
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
    int prefix = Integer.MAX_VALUE;
    for (int i = 0; i < lines.length && prefix != 0; i++) {
      String line = lines[i];
      int indent = 0;
      while (indent < line.length() && Character.isWhitespace(line.charAt(indent))) indent++;
      if (indent == line.length() && (i < lines.length - 1 || ignoreLastLine)) {
        if (!preserveContent) lines[i] = "";
        if (lines.length == 1) prefix = indent;
      }
      else if (indent < prefix) prefix = indent;
    }
    return prefix;
  }
}
