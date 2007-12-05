package com.intellij.formatting;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.codeStyle.CodeStyleSettings;

public class IndentInfo {
  private final int mySpaces;
  private final int myIndentSpaces;
  private final int myLineFeeds;
  private boolean myIsChanged = true;
  private TextRange myInitialTextRange;

  public IndentInfo(final int lineFeeds, final int indentSpaces, final int spaces) {
    mySpaces = spaces;
    myIndentSpaces = indentSpaces;
    myLineFeeds = lineFeeds;
  }

  public int getSpaces() {
    return mySpaces;
  }

  public void setInitialTextRange(final TextRange initialTextRange) {
    myInitialTextRange = initialTextRange;
  }

  public int getIndentSpaces() {
    return myIndentSpaces;
  }

  public int getLineFeeds() {
    return myLineFeeds;
  }

  public String generateNewWhiteSpace(CodeStyleSettings.IndentOptions options) {
    StringBuffer buffer = new StringBuffer();
    StringUtil.repeatSymbol(buffer, '\n', myLineFeeds);

    if (options.USE_TAB_CHARACTER) {
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

  public int getIndentCount(final CodeStyleSettings.IndentOptions indentOptions) {
    return myIndentSpaces/indentOptions.INDENT_SIZE;
  }

  public int getSpacesCount(final CodeStyleSettings.IndentOptions indentOptions) {
    final int indentSpaces = getIndentCount(indentOptions);
    return myIndentSpaces - indentSpaces * indentOptions.INDENT_SIZE + mySpaces;

  }

  public boolean isChanged() {
    return myIsChanged;
  }

  public void setIsChanged(final boolean value) {
    myIsChanged = value;
  }

  public TextRange getInitialTextRange() {
    return myInitialTextRange;
  }
}
