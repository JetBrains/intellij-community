// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;

public final class TestCompilerRefFindUsagesAction extends TestCompilerReferenceServiceAction {
  public TestCompilerRefFindUsagesAction() {
    super("Compiler Reference Find Usages");
  }

  @Override
  protected void startActionFor(@NotNull PsiElement element) {
    CompilerReferenceServiceImpl compilerReferenceService = (CompilerReferenceServiceImpl)CompilerReferenceService.getInstanceIfEnabled(element.getProject());
    CompilerReferenceFindUsagesTestInfo compilerReferenceTestInfo = compilerReferenceService == null ? null : compilerReferenceService.getTestFindUsages(element);
    if (compilerReferenceTestInfo == null) {
      return;
    }
    InternalCompilerRefServiceView.showFindUsages(compilerReferenceTestInfo, element);
  }

  @Override
  protected boolean canBeAppliedFor(@NotNull PsiElement element) {
    return element instanceof PsiMethod || element instanceof PsiField || element instanceof PsiClass;
  }
}
