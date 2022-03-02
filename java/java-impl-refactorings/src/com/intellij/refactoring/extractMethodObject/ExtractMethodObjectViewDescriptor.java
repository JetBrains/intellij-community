// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.extractMethodObject;

import com.intellij.java.JavaBundle;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

public class ExtractMethodObjectViewDescriptor implements UsageViewDescriptor {
  private final PsiMethod myMethod;

  public ExtractMethodObjectViewDescriptor(final PsiMethod method) {
    myMethod = method;
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return new PsiElement[]{myMethod};
  }

  @Override
  public String getProcessedElementsHeader() {
    return JavaBundle.message("header.method.to.be.converted");
  }

  @NotNull
  @Override
  public String getCodeReferencesText(final int usagesCount, final int filesCount) {
    return JavaRefactoringBundle.message("refactoring.extract.method.reference.to.change");
  }
}