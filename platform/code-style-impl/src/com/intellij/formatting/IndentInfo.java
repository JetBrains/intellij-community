// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.formatting;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public final class IndentInfo {

  private final int mySpaces;
  private final int myIndentSpaces;

  private final int myLineFeeds;
  /** @see WhiteSpace#setForceSkipTabulationsUsage(boolean)  */
  private final boolean   myForceSkipTabulationsUsage;
  private boolean myIndentEmptyLines; // Additional indent on empty lines (before the end of code block)

  public IndentInfo(final int lineFeeds, final int indentSpaces, final int spaces) {
    this(lineFeeds, indentSpaces, spaces, false);
  }

  @Override
  public String toString() {
    return "IndentInfo{" +
           "mySpaces=" + mySpaces +
           ", myIndentSpaces=" + myIndentSpaces +
           ", myLineFeeds=" + myLineFeeds +
           ", myForceSkipTabulationsUsage=" + myForceSkipTabulationsUsage +
           ", myIndentEmptyLines=" + myIndentEmptyLines +
           '}';
  }

  public IndentInfo(final int lineFeeds,
                    final int indentSpaces,
                    final int spaces,
                    final boolean forceSkipTabulationsUsage) {
    mySpaces = spaces;
    myIndentSpaces = indentSpaces;
    myLineFeeds = lineFeeds;
    myForceSkipTabulationsUsage = forceSkipTabulationsUsage;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  /**
   * Builds string that contains line feeds, white spaces and tabulation symbols known to the current {@link IndentInfo} object.
   *
   * @param options              indentation formatting options
   */
  public @NotNull String generateNewWhiteSpace(@NotNull CommonCodeStyleSettings.IndentOptions options) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < myLineFeeds; i ++) {
      if (options.KEEP_INDENTS_ON_EMPTY_LINES && i > 0) {
        int spaces = myIndentEmptyLines ? myIndentSpaces + options.INDENT_SIZE : myIndentSpaces;
        generateLineWhitespace(buffer, options, spaces, mySpaces, true);
      }
      buffer.append('\n');
    }
    generateLineWhitespace(buffer, options, myIndentSpaces, mySpaces, !myForceSkipTabulationsUsage || myLineFeeds > 0);
    return buffer.toString();

  }

  private static void generateLineWhitespace(@NotNull StringBuffer buffer,
                                             @NotNull CommonCodeStyleSettings.IndentOptions options,
                                             int indentSpaces,
                                             int alignmentSpaces,
                                             boolean tabsAllowed) {
    if (options.USE_TAB_CHARACTER && tabsAllowed) {
      if (options.SMART_TABS) {
        int tabCount = indentSpaces / options.TAB_SIZE;
        int leftSpaces = indentSpaces - tabCount * options.TAB_SIZE;
        StringUtil.repeatSymbol(buffer, '\t', tabCount);
        StringUtil.repeatSymbol(buffer, ' ', leftSpaces + alignmentSpaces);
      }
      else {
        int size = indentSpaces + alignmentSpaces;
        int tabs = size / options.TAB_SIZE;
        int spaces = size % options.TAB_SIZE;
        StringUtil.repeatSymbol(buffer, '\t', tabs);
        StringUtil.repeatSymbol(buffer, ' ', spaces);
      }
    }
    else {
       int spaces = indentSpaces + alignmentSpaces;
       StringUtil.repeatSymbol(buffer, ' ', spaces);
    }
  }

  @NotNull
  IndentInfo setIndentEmptyLines(boolean indentEmptyLines) {
    myIndentEmptyLines = indentEmptyLines;
    return this;
  }
}
