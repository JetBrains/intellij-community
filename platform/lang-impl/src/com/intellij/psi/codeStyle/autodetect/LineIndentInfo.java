// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.autodetect;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class LineIndentInfo {
  public static final LineIndentInfo EMPTY_LINE = new LineIndentInfo(LineType.EMPTY_LINE, -1);
  public static final LineIndentInfo LINE_WITH_COMMENT = new LineIndentInfo(LineType.LINE_WITH_COMMENT, -1);
  public static final LineIndentInfo LINE_WITH_TABS = new LineIndentInfo(LineType.LINE_WITH_TABS, -1);
  public static final LineIndentInfo LINE_WITH_CONTINUATION_INDENT = new LineIndentInfo(LineType.LINE_WITH_CONTINUATION_INDENT, -1);
  public static final LineIndentInfo LINE_WITH_NOT_COUNTABLE_INDENT = new LineIndentInfo(LineType.LINE_WITH_NOT_COUNTABLE_INDENT, -1);

  private final int myIndentSize;
  private final LineType myType;

  private LineIndentInfo(@NotNull LineType type, int indentSize) {
    myType = type;
    myIndentSize = indentSize;
  }

  public static @NotNull LineIndentInfo newNormalIndent(int indentSize) {
    return new LineIndentInfo(LineType.LINE_WITH_NORMAL_WHITESPACE_INDENT, indentSize);
  }

  public int getIndentSize() {
    return myIndentSize;
  }

  public boolean isLineWithNormalIndent() {
    return myType == LineType.LINE_WITH_NORMAL_WHITESPACE_INDENT;
  }

  public boolean isLineWithTabs() {
    return myType == LineType.LINE_WITH_TABS;
  }

  private enum LineType {
    EMPTY_LINE,
    LINE_WITH_COMMENT,
    LINE_WITH_TABS,
    LINE_WITH_NORMAL_WHITESPACE_INDENT,
    LINE_WITH_CONTINUATION_INDENT,
    LINE_WITH_NOT_COUNTABLE_INDENT
  }
}
