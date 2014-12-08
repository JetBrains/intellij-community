package com.intellij.openapi.util.diff.tools.util.base;

import org.jetbrains.annotations.NotNull;

public enum HighlightPolicy {
  DO_NOT_HIGHLIGHT("Do Not Highlight"),
  BY_LINE("By Line"),
  BY_WORD("By Word"),
  BY_WORD_SPLIT("By Word In Chunks");

  @NotNull private final String myText;

  HighlightPolicy(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  public boolean isShouldCompare() {
    return this != DO_NOT_HIGHLIGHT;
  }

  public boolean isFineFragments() {
    return this == BY_WORD || this == BY_WORD_SPLIT;
  }

  public boolean isShouldSquash() {
    return this == BY_WORD || this == BY_LINE;
  }
}
