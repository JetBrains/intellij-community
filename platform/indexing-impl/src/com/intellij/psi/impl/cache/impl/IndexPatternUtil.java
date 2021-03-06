// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl;

import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.search.IndexPatternProvider;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;

public final class IndexPatternUtil {

  public static IndexPattern @NotNull [] getIndexPatterns() {
    ArrayList<IndexPattern> result = new ArrayList<>();
    for (IndexPatternProvider provider : IndexPatternProvider.EP_NAME.getExtensionList()) {
      result.addAll(Arrays.asList(provider.getIndexPatterns()));
    }
    return result.toArray(new IndexPattern[0]);
  }
}
