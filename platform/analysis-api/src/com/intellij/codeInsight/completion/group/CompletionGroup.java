// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.group;

import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;

/**
 * Represents a grouping structure used in completion mechanisms.
 */
@ApiStatus.Internal
public record CompletionGroup(
  int order,
  @Nls(capitalization = Nls.Capitalization.Title) String displayName) {
  @ApiStatus.Internal
  public static final Key<CompletionGroup> COMPLETION_GROUP_KEY = Key.create("completion.group.key");

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;

    CompletionGroup that = (CompletionGroup)o;
    return displayName.equals(that.displayName);
  }

  @Override
  public int hashCode() {
    return displayName.hashCode();
  }
}
