/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiKeyword;

/**
 * @author peter
*/
class KeywordLookupItem extends LookupItem<PsiKeyword> {
  public KeywordLookupItem(final PsiKeyword keyword) {
    super(keyword, keyword.getText());
    setBold();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof KeywordLookupItem && getObject().equals(((KeywordLookupItem)o).getObject());
  }

  @Override
  public int hashCode() {
    return getObject().hashCode();
  }
}
