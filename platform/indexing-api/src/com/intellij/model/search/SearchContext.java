// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.psi.search.UsageSearchContext;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public enum SearchContext {

  IN_CODE(UsageSearchContext.IN_CODE),
  IN_CODE_HOSTS(UsageSearchContext.IN_FOREIGN_LANGUAGES),
  IN_COMMENTS(UsageSearchContext.IN_COMMENTS),
  IN_STRINGS(UsageSearchContext.IN_STRINGS),
  IN_PLAIN_TEXT(UsageSearchContext.IN_PLAIN_TEXT),
  ;

  private final short mask;

  SearchContext(short mask) {
    this.mask = mask;
  }

  @Contract(pure = true)
  public static short mask(@NotNull Collection<SearchContext> contexts) {
    short result = 0;
    for (SearchContext context : contexts) {
      result |= context.mask;
    }
    return result;
  }
}
