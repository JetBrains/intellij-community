// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.PrefixMatcher;
import org.jetbrains.annotations.NotNull;

public interface WeighingContext {
  @NotNull
  String itemPattern(@NotNull LookupElement element);

  @NotNull
  PrefixMatcher itemMatcher(@NotNull LookupElement item);

}
