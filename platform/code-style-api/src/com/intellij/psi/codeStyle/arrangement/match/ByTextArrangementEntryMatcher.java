// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle.arrangement.match;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.arrangement.ArrangementEntry;
import com.intellij.psi.codeStyle.arrangement.TextAwareArrangementEntry;
import org.jetbrains.annotations.NotNull;

/**
 * @author Svetlana.Zemlyanskaya
 */
public class ByTextArrangementEntryMatcher implements ArrangementEntryMatcher {
  private final @NotNull String myText;

  public ByTextArrangementEntryMatcher(@NotNull String text) {
    myText = text;
  }

  @Override
  public boolean isMatched(@NotNull ArrangementEntry entry) {
    if (entry instanceof TextAwareArrangementEntry) {
      return StringUtil.equals(((TextAwareArrangementEntry)entry).getText(), myText);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return myText.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ByTextArrangementEntryMatcher matcher)) {
      return false;
    }

    if (!myText.equals(matcher.myText)) {
      return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return "with text " + myText;
  }
}
