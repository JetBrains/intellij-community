/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.usages.impl.rules;

import com.intellij.usages.rules.UsageFilteringRule;
import com.intellij.usages.rules.PsiElementUsage;
import com.intellij.usages.Usage;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author Eugene Zhuravlev
 *         Date: Jan 17, 2005
 */
public class ImportFilteringRule implements UsageFilteringRule{
  public boolean isVisible(Usage usage) {
    if (usage instanceof PsiElementUsage) {
      final PsiElement psiElement = ((PsiElementUsage)usage).getElement();
      final PsiFile containingFile = psiElement.getContainingFile();
      if (containingFile instanceof PsiJavaFile) {
        // check whether the element is in the import list
        final PsiImportList importList = PsiTreeUtil.getParentOfType(psiElement, PsiImportList.class, true);
        return importList == null;
      }
    }
    return true;
  }
}
