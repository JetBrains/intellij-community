
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inline;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.*;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class InlineViewDescriptor implements UsageViewDescriptor{

  private final PsiElement myElement;

  InlineViewDescriptor(PsiElement element) {
    myElement = element;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[] {myElement};
  }

  @Override
  public String getProcessedElementsHeader() {
    if (myElement instanceof PsiField) {
      return JavaRefactoringBundle.message("inline.field.elements.header");
    }
    if (myElement instanceof PsiVariable) {
      return JavaRefactoringBundle.message("inline.vars.elements.header");
    }
    if (myElement instanceof PsiClass) {
      return JavaRefactoringBundle.message("inline.class.elements.header");
    }
    if (myElement instanceof PsiMethod) {
      return JavaRefactoringBundle.message("inline.method.elements.header");
    }
    return JavaRefactoringBundle.message("inline.element.unknown.header");
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("invocations.to.be.inlined", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return JavaRefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
