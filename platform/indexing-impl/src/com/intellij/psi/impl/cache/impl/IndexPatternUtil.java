// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl;

import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class IndexPatternUtil {
  @NotNull
  public static List<IndexPatternProvider> getIndexPatternProviders() {
    return IndexPatternProvider.EP_NAME.getExtensionList();
  }

  public static int getIndexPatternCount() {
    return getIndexPatternProviders().stream().mapToInt(provider -> provider.getIndexPatterns().length).sum();
  }

  public static IndexPattern @NotNull [] getIndexPatterns() {
    IndexPattern[] result = new IndexPattern[getIndexPatternCount()];
    int destIndex = 0;
    for (IndexPatternProvider provider : getIndexPatternProviders()) {
      for (IndexPattern pattern : provider.getIndexPatterns()) {
        result[destIndex++] = pattern;
      }
    }
    return result;
  }
}
