// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.base;

import com.intellij.diff.comparison.InnerFragmentsPolicy;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum HighlightPolicy {
  BY_LINE("option.highlighting.policy.lines"),
  BY_WORD("option.highlighting.policy.words"),
  BY_WORD_SPLIT("option.highlighting.policy.split"),
  BY_CHAR("option.highlighting.policy.symbols"),
  DO_NOT_HIGHLIGHT("option.highlighting.policy.none");

  private final @NotNull String myTextKey;

  HighlightPolicy(@NotNull @PropertyKey(resourceBundle = DiffBundle.BUNDLE) String textKey) {
    myTextKey = textKey;
  }

  public @Nls @NotNull String getText() {
    return DiffBundle.message(myTextKey);
  }

  public boolean isShouldCompare() {
    return this != DO_NOT_HIGHLIGHT;
  }

  public boolean isFineFragments() {
    return getFragmentsPolicy() != InnerFragmentsPolicy.NONE;
  }

  public boolean isShouldSquash() {
    return this != BY_WORD_SPLIT;
  }

  public @NotNull InnerFragmentsPolicy getFragmentsPolicy() {
    return switch (this) {
      case BY_WORD, BY_WORD_SPLIT -> InnerFragmentsPolicy.WORDS;
      case BY_CHAR -> InnerFragmentsPolicy.CHARS;
      case BY_LINE, DO_NOT_HIGHLIGHT -> InnerFragmentsPolicy.NONE;
    };
  }
}
