// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.model.search;

import com.intellij.psi.search.UsageSearchContext;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public final class SearchContext {

  /**
   * @deprecated use {@link #inCode}
   */
  @Deprecated
  public static final SearchContext IN_CODE = new SearchContext("IN_CODE", UsageSearchContext.IN_CODE);

  /**
   * @deprecated use {@link #inCodeHosts}
   */
  @Deprecated
  public static final SearchContext IN_CODE_HOSTS = new SearchContext("IN_CODE_HOSTS", UsageSearchContext.IN_FOREIGN_LANGUAGES);

  /**
   * @deprecated use {@link #inComments}
   */
  @Deprecated
  public static final SearchContext IN_COMMENTS = new SearchContext("IN_COMMENTS", UsageSearchContext.IN_COMMENTS);

  /**
   * @deprecated use {@link #inStrings}
   */
  @Deprecated
  public static final SearchContext IN_STRINGS = new SearchContext("IN_STRINGS", UsageSearchContext.IN_STRINGS);

  /**
   * @deprecated use {@link #inPlainText}
   */
  @Deprecated
  public static final SearchContext IN_PLAIN_TEXT = new SearchContext("IN_PLAIN_TEXT", UsageSearchContext.IN_PLAIN_TEXT);

  private final @NonNls @NotNull String debugName;
  private final short mask;

  private SearchContext(@NonNls @NotNull String debugName, short mask) {
    this.debugName = debugName;
    this.mask = mask;
  }

  @Override
  public String toString() {
    return debugName;
  }

  public static @NotNull SearchContext inCode() {
    return IN_CODE;
  }

  public static @NotNull SearchContext inCodeHosts() {
    return IN_CODE_HOSTS;
  }

  public static @NotNull SearchContext inComments() {
    return IN_COMMENTS;
  }

  public static @NotNull SearchContext inStrings() {
    return IN_STRINGS;
  }

  public static @NotNull SearchContext inPlainText() {
    return IN_PLAIN_TEXT;
  }

  @Internal
  @Contract(pure = true)
  public static short mask(@NotNull Collection<SearchContext> contexts) {
    short result = 0;
    for (SearchContext context : contexts) {
      result |= context.mask;
    }
    return result;
  }
}
