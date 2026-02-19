// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NameAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ByNameArrangementEntryMatcher extends AbstractRegexpArrangementMatcher {

  public ByNameArrangementEntryMatcher(@NotNull String pattern) {
    super(pattern);
  }

  @Override
  protected @Nullable String getTextToMatch(@NotNull ArrangementEntry entry) {
    if (entry instanceof NameAwareArrangementEntry) {
      return  ((NameAwareArrangementEntry)entry).getName();
    }
    else {
      return null;
    }
  }
}
