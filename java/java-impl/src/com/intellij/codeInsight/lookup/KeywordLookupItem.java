// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.FilterUtil;
import org.jetbrains.annotations.NotNull;

public class KeywordLookupItem extends LookupElement implements TypedLookupItem {
  private final PsiElement myPosition;
  private final PsiKeyword myKeyword;

  public KeywordLookupItem(final PsiKeyword keyword, @NotNull PsiElement position) {
    myKeyword = keyword;
    myPosition = position;
  }

  @Override
  public @NotNull Object getObject() {
    return myKeyword;
  }

  @Override
  public @NotNull String getLookupString() {
    return myKeyword.getText();
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.GIVE_CHANCE_TO_OVERWRITE;
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof KeywordLookupItem && getLookupString().equals(((KeywordLookupItem)o).getLookupString());
  }

  @Override
  public int hashCode() {
    return getLookupString().hashCode();
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
    presentation.setItemTextBold(true);
  }

  @Override
  public PsiType getType() {
    return FilterUtil.getKeywordItemType(myPosition, getLookupString());
  }
}
