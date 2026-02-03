// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.compiler.JavaCompilerBundle;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TestCompilerRefInheritanceAction extends TestCompilerHierarchyBaseAction {
  public TestCompilerRefInheritanceAction() {
    super(JavaCompilerBundle.message("action.compiler.reference.direct.inheritor.search.text"));
  }

  @Override
  protected boolean canBeAppliedFor(@NotNull PsiElement element) {
    return element instanceof PsiClass;
  }

  @Override
  protected @Nullable CompilerReferenceHierarchyTestInfo getHierarchy(@NotNull PsiElement element,
                                                                      @NotNull CompilerReferenceServiceImpl refService,
                                                                      @NotNull GlobalSearchScope scope) {
    return refService.getTestHierarchy((PsiNamedElement)element, scope, JavaFileType.INSTANCE);
  }
}
