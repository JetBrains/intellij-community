/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.FilterUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
*/
public class KeywordLookupItem extends LookupItem<PsiKeyword> implements TypedLookupItem {
  private final PsiElement myPosition;

  public KeywordLookupItem(final PsiKeyword keyword, @NotNull PsiElement position) {
    super(keyword, keyword.getText());
    myPosition = position;
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
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setItemText(getLookupString());
    presentation.setItemTextBold(true);
  }

  @Override
  public PsiType getType() {
    return FilterUtil.getKeywordItemType(myPosition, getLookupString());
  }
}
