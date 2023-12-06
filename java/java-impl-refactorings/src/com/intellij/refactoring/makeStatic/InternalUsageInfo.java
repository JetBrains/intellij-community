// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.makeStatic;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

class InternalUsageInfo extends UsageInfo {
  final PsiElement myReferencedElement;
  private Boolean myIsInsideAnonymous;

  InternalUsageInfo(PsiElement element, @NotNull PsiElement referencedElement) {
    super(element);
    myReferencedElement = referencedElement;
    myIsInsideAnonymous = null;
    isInsideAnonymous();
  }

  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }

  public boolean isInsideAnonymous() {
    if(myIsInsideAnonymous == null) {
      myIsInsideAnonymous = Boolean.valueOf(RefactoringUtil.isInsideAnonymousOrLocal(getElement(), null));
    }

    return myIsInsideAnonymous.booleanValue();
  }

  public boolean isWriting() {
    return myReferencedElement instanceof PsiField &&
              getElement() instanceof PsiReferenceExpression && PsiUtil.isAccessedForWriting(((PsiReferenceExpression)getElement()));
  }
}
