// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff;

import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated See {@link com.intellij.openapi.util.text.LineTokenizer} and
 * {@link com.intellij.openapi.util.text.StringUtil#tokenize(String, String)}.
 */
@Deprecated
public class LineTokenizer extends LineTokenizerBase<String> {
  private final char[] myChars;
  private final String myText;

  public LineTokenizer(@NotNull String text) {
    myChars = text.toCharArray();
    myText = text;
  }

  public String @NotNull [] execute() {
    ArrayList<String> lines = new ArrayList<>();
    doExecute(lines);
    return ArrayUtilRt.toStringArray(lines);
  }

  @Override
  protected void addLine(List<? super String> lines, int start, int end, boolean appendNewLine) {
    if (appendNewLine) {
      lines.add(myText.substring(start, end) + "\n");
    }
    else {
      lines.add(myText.substring(start, end));
    }
  }

  @Override
  protected char charAt(int index) {
    return myChars[index];
  }

  @Override
  protected int length() {
    return myChars.length;
  }

  @Override
  protected @NotNull String substring(int start, int end) {
    return myText.substring(start, end);
  }

  public static @NotNull String concatLines(String @NotNull [] lines) {
    StringBuilder buffer = new StringBuilder();
    for (String line : lines) {
      buffer.append(line);
    }
    return buffer.substring(0, buffer.length());
  }

  public static @NotNull String correctLineSeparators(@NotNull String text) {
    return concatLines(new LineTokenizer(text).execute());
  }

}
