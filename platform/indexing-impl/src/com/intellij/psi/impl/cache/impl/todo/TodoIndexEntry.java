// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.cache.impl.todo;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class TodoIndexEntry {
  @Internal
  public final String pattern;
  @Internal
  public final boolean caseSensitive;

  @Internal
  public TodoIndexEntry(@NotNull String pattern, final boolean caseSensitive) {
    this.pattern = pattern;
    this.caseSensitive = caseSensitive;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final TodoIndexEntry that = (TodoIndexEntry)o;

    return caseSensitive == that.caseSensitive && pattern.equals(that.pattern);
  }

  @Override
  public int hashCode() {
    int result = pattern.hashCode();
    result = 31 * result + (caseSensitive ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    return "TodoIndexEntry[pattern=" + pattern + ", caseSensitive=" + caseSensitive + "]";
  }
}
