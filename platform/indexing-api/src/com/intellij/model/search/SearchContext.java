// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.psi.search.UsageSearchContext;

public enum SearchContext {

  IN_CODE(UsageSearchContext.IN_CODE),
  IN_COMMENTS(UsageSearchContext.IN_COMMENTS),
  IN_STRINGS(UsageSearchContext.IN_STRINGS),
  IN_FOREIGN_LANGUAGES(UsageSearchContext.IN_FOREIGN_LANGUAGES),
  IN_PLAIN_TEXT(UsageSearchContext.IN_PLAIN_TEXT),
  ;

  public final short mask;

  SearchContext(short mask) {
    this.mask = mask;
  }
}
