// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.util.RefactoringUIUtil;
import org.jetbrains.annotations.NotNull;

public class LocalHidesRenamedLocalUsageInfo extends UnresolvableCollisionUsageInfo {
  private final @NotNull PsiVariable myConflictingElement;

  public LocalHidesRenamedLocalUsageInfo(@NotNull PsiVariable element, PsiElement referencedElement) {
    super(element, referencedElement);
    myConflictingElement = element;
  }

  @Override
  public String getDescription() {
    return JavaRefactoringBundle.message("there.is.already.a.0.it.will.conflict.with.the.renamed.1",
                                         RefactoringUIUtil.getDescription(myConflictingElement, true));
  }
  
  @Override
  public @NlsContexts.PopupTitle String getShortDescription() {
    return JavaRefactoringBundle.message("there.is.already.a.0.it.will.conflict.with.the.renamed.short",
                                         myConflictingElement.getName());
  }
}
