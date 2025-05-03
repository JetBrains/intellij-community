// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util.base;

import com.intellij.diff.comparison.ComparisonPolicy;
import com.intellij.openapi.diff.DiffBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

public enum IgnorePolicy {
  DEFAULT("option.ignore.policy.none"),
  TRIM_WHITESPACES("option.ignore.policy.trim"),
  IGNORE_WHITESPACES("option.ignore.policy.whitespaces"),
  IGNORE_WHITESPACES_CHUNKS("option.ignore.policy.whitespaces.empty.lines"),
  FORMATTING("option.ignore.policy.formatting"),
  IGNORE_LANGUAGE_SPECIFIC_CHANGES("option.ignore.policy.language.specific.changes");

  private final @NotNull String myTextKey;

  IgnorePolicy(@NotNull @PropertyKey(resourceBundle = DiffBundle.BUNDLE) String textKey) {
    myTextKey = textKey;
  }

  public @Nls @NotNull String getText() {
    return DiffBundle.message(myTextKey);
  }

  public @NotNull ComparisonPolicy getComparisonPolicy() {
    return switch (this) {
      case DEFAULT, FORMATTING, IGNORE_LANGUAGE_SPECIFIC_CHANGES -> ComparisonPolicy.DEFAULT;
      case TRIM_WHITESPACES -> ComparisonPolicy.TRIM_WHITESPACES;
      case IGNORE_WHITESPACES, IGNORE_WHITESPACES_CHUNKS -> ComparisonPolicy.IGNORE_WHITESPACES;
    };
  }

  public boolean isShouldSquash() {
    return this != IGNORE_LANGUAGE_SPECIFIC_CHANGES;
  }

  public boolean isShouldTrimChunks() {
    return this == IGNORE_WHITESPACES_CHUNKS;
  }
}