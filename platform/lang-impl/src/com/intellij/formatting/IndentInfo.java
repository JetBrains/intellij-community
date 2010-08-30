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
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class IndentInfo {

  private final int mySpaces;
  private final int myIndentSpaces;
  private final int myLineFeeds;

  /** @see WhiteSpace#setForceSkipTabulationsUsage(boolean)  */
  private boolean   myForceSkipTabulationsUsage;

  public IndentInfo(final int lineFeeds, final int indentSpaces, final int spaces) {
    this(lineFeeds, indentSpaces, spaces, false);
  }

  public IndentInfo(final int lineFeeds, final int indentSpaces, final int spaces, final boolean forceSkipTabulationsUsage) {
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
  public String generateNewWhiteSpace(CodeStyleSettings.IndentOptions options) {
    StringBuffer buffer = new StringBuffer();
    StringUtil.repeatSymbol(buffer, '\n', myLineFeeds);

    if (options.USE_TAB_CHARACTER && !myForceSkipTabulationsUsage) {
      if (options.SMART_TABS) {
        int tabCount = myIndentSpaces / options.TAB_SIZE;
        int leftSpaces = myIndentSpaces - tabCount * options.TAB_SIZE;
        StringUtil.repeatSymbol(buffer, '\t', tabCount);
        StringUtil.repeatSymbol(buffer, ' ', leftSpaces + mySpaces);
      }
      else {
        int size = getTotalSpaces();
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
      StringUtil.repeatSymbol(buffer, ' ', getTotalSpaces());
    }

    return buffer.toString();

  }

  public int getTotalSpaces() {
    return myIndentSpaces + mySpaces;
  }
}
