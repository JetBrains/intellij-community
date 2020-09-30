// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.largeFilesEditor.search;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NonNls;

public class SearchResult {

  public static final Key<SearchResult> KEY = new Key<>("lfe.SearchResult");

  public final Position startPosition;
  public final Position endPostion;
  public final @NlsSafe String contextPrefix;
  public final @NlsSafe String foundString;
  public final @NlsSafe String contextPostfix;

  public SearchResult(long startPageNumber,
                      int startOffsetInPage,
                      long endPageNumber,
                      int endOffsetInPage,
                      String contextPrefix,
                      String foundString,
                      String contextPostfix) {
    startPosition = new Position(startPageNumber, startOffsetInPage);
    endPostion = new Position(endPageNumber, endOffsetInPage);
    this.contextPrefix = contextPrefix == null ? "" : contextPrefix;
    this.foundString = foundString == null ? "" : foundString;
    this.contextPostfix = contextPostfix == null ? "" : contextPostfix;
  }

  @Override
  public @NonNls String toString() {
    return String.format("p%ds%d-p%ds%d: pref{%s},orig{%s},post{%s}",
                         startPosition.pageNumber, startPosition.symbolOffsetInPage,
                         endPostion.pageNumber, endPostion.symbolOffsetInPage,
                         contextPrefix, foundString, contextPostfix);
  }

  @Override
  public boolean equals(Object target) {
    if (this == target) {
      return true;
    }

    if (target instanceof SearchResult) {
      SearchResult targetResult = (SearchResult)target;
      if (startPosition.equals(targetResult.startPosition)
          && endPostion.equals(targetResult.endPostion)
          && contextPrefix.equals(targetResult.contextPrefix)
          && foundString.equals(targetResult.foundString)
          && contextPostfix.equals(targetResult.contextPostfix)) {
        return true;
      }
    }
    return false;
  }
}
