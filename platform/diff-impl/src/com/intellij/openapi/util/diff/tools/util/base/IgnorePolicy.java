package com.intellij.openapi.util.diff.tools.util.base;

import com.intellij.openapi.util.diff.comparison.ComparisonPolicy;
import org.jetbrains.annotations.NotNull;

public enum IgnorePolicy {
  DEFAULT("Do Not Ignore"),
  TRIM_WHITESPACES("Trim Whitespaces"),
  IGNORE_WHITESPACES("Ignore Whitespaces"),
  IGNORE_WHITESPACES_CHUNKS("Ignore Whitespaces And Empty Lines");

  @NotNull private final String myText;

  IgnorePolicy(@NotNull String text) {
    myText = text;
  }

  @NotNull
  public String getText() {
    return myText;
  }

  @NotNull
  public ComparisonPolicy getComparisonPolicy() {
    switch (this) {
      case DEFAULT:
        return ComparisonPolicy.DEFAULT;
      case TRIM_WHITESPACES:
        return ComparisonPolicy.TRIM_WHITESPACES;
      case IGNORE_WHITESPACES:
        return ComparisonPolicy.IGNORE_WHITESPACES;
      case IGNORE_WHITESPACES_CHUNKS:
        return ComparisonPolicy.IGNORE_WHITESPACES;
      default:
        throw new IllegalArgumentException(this.name());
    }
  }

  public boolean isShouldTrimChunks() {
    return this == IGNORE_WHITESPACES_CHUNKS;
  }
}