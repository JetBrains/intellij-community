/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.find.findUsages;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.lang.findUsages.LanguageFindUsages;

/**
 * @author peter
*/
public class DefaultFindUsagesHandlerFactory extends FindUsagesHandlerFactory {
  public boolean canFindUsages(final PsiElement element) {
    if (element instanceof PsiFile) {
      if (((PsiFile)element).getVirtualFile() == null) return false;
    }
    else if (!LanguageFindUsages.INSTANCE.forLanguage(element.getLanguage()).canFindUsagesFor(element)) {
      return false;
    }
    return true;
  }

  public FindUsagesHandler createFindUsagesHandler(final PsiElement element, final boolean forHighlightUsages) {
    if (canFindUsages(element)) {
      return new FindUsagesHandler(element){};
    }
    return null;
  }
}
