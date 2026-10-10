// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteReferenceSimpleDeleteUsageInfo extends SafeDeleteReferenceUsageInfo implements ModPlanSafeDeleteUsage {
  private static final Logger LOG = Logger.getInstance(SafeDeleteReferenceSimpleDeleteUsageInfo.class);
  public SafeDeleteReferenceSimpleDeleteUsageInfo(PsiElement element, PsiElement referencedElement, boolean isSafeDelete) {
    super(element, referencedElement, isSafeDelete);
  }

  public SafeDeleteReferenceSimpleDeleteUsageInfo(PsiElement element, PsiElement referencedElement,
                                                  int startOffset, int endOffset, boolean isNonCodeUsage, boolean isSafeDelete) {
    super(element, referencedElement, startOffset, endOffset, isNonCodeUsage, isSafeDelete);
  }

  @Override
  public void deleteElement() throws IncorrectOperationException {
    if(isSafeDelete()) {
      PsiElement element = getElement();
      LOG.assertTrue(element != null);
      element.delete();
    }
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    PsiElement element = getElement();
    return isSafeDelete() && element != null ? ModPlanEdit.delete(element) : null;
  }
}
