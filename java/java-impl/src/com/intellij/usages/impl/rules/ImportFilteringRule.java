// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.rules.PsiElementUsage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 */
public final class ImportFilteringRule extends com.intellij.usages.rules.ImportFilteringRule {
  @Override
  public boolean isVisible(@NotNull Usage usage, @NotNull UsageTarget @NotNull [] targets) {
    final PsiElement psiElement = usage instanceof PsiElementUsage? ((PsiElementUsage)usage).getElement() : null;
    if (psiElement != null) {
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
