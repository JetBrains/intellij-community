// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RefactoringUIUtil;

public class LocalHidesRenamedLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final PsiElement myConflictingElement;

  public LocalHidesRenamedLocalUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
    myConflictingElement = element;
  }

  @Override
  public String getDescription() {
    return JavaRefactoringBundle.message("there.is.already.a.0.it.will.conflict.with.the.renamed.1",
                                         RefactoringUIUtil.getDescription(myConflictingElement, true));
  }
}
