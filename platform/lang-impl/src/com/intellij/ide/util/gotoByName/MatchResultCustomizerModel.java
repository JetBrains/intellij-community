// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.util.gotoByName;

import com.intellij.psi.codeStyle.MinusculeMatcher;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface MatchResultCustomizerModel {
  /**
   * Sometimes when integrating external providers we need to apply the same matcher to a different compound name, so that we could manipulate either
   * the matching degree or the result of a match against a compound name.
   */
  @Nullable
  MatchResult getCustomRulesMatchResult(@NotNull MinusculeMatcher fullMatcher, @NotNull String pattern, @NotNull MinusculeMatcher nameMatcher, @Nullable String name);
}
