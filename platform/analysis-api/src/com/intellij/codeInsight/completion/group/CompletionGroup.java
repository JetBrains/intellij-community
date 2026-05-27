// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.group;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a grouping structure used in completion mechanisms.
 */
@ApiStatus.Internal
public record CompletionGroup(
  int order,
  @Nls(capitalization = Nls.Capitalization.Title) String displayName
) {

  private static final Key<CompletionGroup> COMPLETION_GROUP_KEY = Key.create("completion.group.key");

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

  public void installTo(@NotNull LookupElement element) {
    element.putUserData(COMPLETION_GROUP_KEY, this);
  }

  public static @Nullable CompletionGroup get(@NotNull LookupElement element) {
    return element.getUserData(COMPLETION_GROUP_KEY);
  }

  public static void drop(@NotNull LookupElement element) {
    element.putUserData(COMPLETION_GROUP_KEY, null);
  }
}
