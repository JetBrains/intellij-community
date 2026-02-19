// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup.impl;

import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class EmptyLookupItem extends LookupElement {
  private final String myText;
  private final boolean myLoading;

  public EmptyLookupItem(final String s, boolean loading) {
    myText = s;
    myLoading = loading;
  }

  @Override
  public @NotNull String getLookupString() {
    return "             ";
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setItemText(myText);
  }

  public boolean isLoading() {
    return myLoading;
  }
}
