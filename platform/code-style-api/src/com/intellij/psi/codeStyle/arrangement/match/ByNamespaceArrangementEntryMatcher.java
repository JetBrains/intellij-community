// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.NamespaceAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ByNamespaceArrangementEntryMatcher extends AbstractRegexpArrangementMatcher {

  public ByNamespaceArrangementEntryMatcher(@NotNull String pattern) {
    super(pattern);
  }

  @Override
  protected @Nullable String getTextToMatch(@NotNull ArrangementEntry entry) {
    if (entry instanceof NamespaceAwareArrangementEntry) {
      return ((NamespaceAwareArrangementEntry)entry).getNamespace();
    }
    else {
      return null;
    }
  }
}
