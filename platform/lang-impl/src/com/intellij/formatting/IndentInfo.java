/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.formatting;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;

public class IndentInfo {

  private final int mySpaces;
  private final int myIndentSpaces;
  private final int myHiddenSpaces;

  private final int myLineFeeds;
  /** @see WhiteSpace#setForceSkipTabulationsUsage(boolean)  */
  private final boolean   myForceSkipTabulationsUsage;
  private boolean myIndentEmptyLines; // Additional indent on empty lines (before the end of code block)

  public IndentInfo(final int lineFeeds, final int indentSpaces, final int spaces) {
    this(lineFeeds, indentSpaces, spaces, 0, false);
  }

  public IndentInfo(final int lineFeeds,
                    final int indentSpaces,
                    final int spaces,
                    final int hiddenSpaces)
  {
    this(lineFeeds, indentSpaces, spaces, hiddenSpaces, false);
  }

  public IndentInfo(final int lineFeeds,
                    final int indentSpaces,
                    final int spaces,
                    final boolean forceSkipTabulationsUsage)
  {
    this(lineFeeds, indentSpaces, spaces, 0, forceSkipTabulationsUsage);
  }

  public IndentInfo(final int lineFeeds, final int indentSpaces, final int spaces, final int hiddenSpaces, final boolean forceSkipTabulationsUsage) {
    mySpaces = spaces;
    myIndentSpaces = indentSpaces;
    myLineFeeds = lineFeeds;
    myForceSkipTabulationsUsage = forceSkipTabulationsUsage;
    myHiddenSpaces = hiddenSpaces;
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
  public String generateNewWhiteSpace(CommonCodeStyleSettings.IndentOptions options) {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < myLineFeeds; i ++) {
      if (options.KEEP_INDENTS_ON_EMPTY_LINES && i > 0) {
        int spaces = myIndentEmptyLines ? myIndentSpaces + options.INDENT_SIZE : myIndentSpaces;
        generateLineWhitespace(buffer, options, spaces, 0, true);
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
        if (tabCount > 0) {
          StringUtil.repeatSymbol(buffer, '\t', tabCount);
        }
        if (leftSpaces + alignmentSpaces > 0) {
          StringUtil.repeatSymbol(buffer, ' ', leftSpaces + alignmentSpaces);
        }
      }
      else {
        int size = indentSpaces + alignmentSpaces;
        while (size > 0) {
          if (size >= options.TAB_SIZE) {
            buffer.append('\t');
            size -= options.TAB_SIZE;
          }
          else {
            buffer.append(' ');
            size--;
          }
        }
      }
    }
    else {
      int spaces = indentSpaces + alignmentSpaces;
      if (spaces > 0) {
        StringUtil.repeatSymbol(buffer, ' ', spaces);
      }
    }
  }

  public IndentInfo setIndentEmptyLines(boolean indentEmptyLines) {
    myIndentEmptyLines = indentEmptyLines;
    return this;
  }
}
