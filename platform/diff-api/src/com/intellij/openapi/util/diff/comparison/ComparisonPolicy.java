package com.intellij.openapi.util.diff.comparison;

import org.jetbrains.annotations.NotNull;

public enum ComparisonPolicy {
  DEFAULT("Default"),
  TRIM_WHITESPACES("Trim Whitespaces"),
  IGNORE_WHITESPACES("Ignore Whitespaces");

  @NotNull private final String myText;

  ComparisonPolicy(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }
}
