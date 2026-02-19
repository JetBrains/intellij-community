// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class IndentInside {
  public int whiteSpaces = 0;
  public int tabs = 0;

  public IndentInside() {
  }

  public IndentInside(int whiteSpaces, int tabs) {
    this.whiteSpaces = whiteSpaces;
    this.tabs = tabs;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final IndentInside indent = (IndentInside)o;

    if (tabs != indent.tabs) return false;
    return whiteSpaces == indent.whiteSpaces;
  }

  @Override
  public int hashCode() {
    int result;
    result = whiteSpaces;
    result = 29 * result + tabs;
    return result;
  }

  public int getTabsCount(final CommonCodeStyleSettings.IndentOptions options) {
    final int tabsFromSpaces = whiteSpaces / options.TAB_SIZE;
    return tabs + tabsFromSpaces;
  }

  public int getSpacesCount(final CommonCodeStyleSettings.IndentOptions options) {
    return whiteSpaces + tabs * options.TAB_SIZE;
  }

  static @NotNull IndentInside getLastLineIndent(final @NotNull CharSequence text) {
    CharSequence lastLine = getLastLine(text);
    return createIndentOn(lastLine);
  }

  public static @NotNull IndentInside createIndentOn(final @Nullable CharSequence lastLine) {
    final IndentInside result = new IndentInside();
    if (lastLine == null) {
      return result;
    }
    for (int i = 0; i < lastLine.length(); i++) {
      if (lastLine.charAt(i) == ' ') result.whiteSpaces += 1;
      if (lastLine.charAt(i) == '\t') result.tabs += 1;
    }
    return result;
  }

  public static @NotNull CharSequence getLastLine(final @NotNull CharSequence text) {
    int i = CharArrayUtil.shiftBackwardUntil(text, text.length() - 1, "\n");
    if (i < 0) {
      return text;
    }
    else if (i >= text.length() - 1) {
      return "";
    }
    else {
      return text.subSequence(i + 1, text.length());
    }
  }

  @Override
  public String toString() {
    return String.format("spaces: %d, tabs: %d", whiteSpaces, tabs);
  }
}
