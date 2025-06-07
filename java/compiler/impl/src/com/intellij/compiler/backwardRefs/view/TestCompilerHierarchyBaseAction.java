// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs.view;

import com.intellij.compiler.CompilerReferenceService;
import com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class TestCompilerHierarchyBaseAction extends TestCompilerReferenceServiceAction {
  public TestCompilerHierarchyBaseAction(@NlsActions.ActionText String text) {
    super(text);
  }

  @Override
  protected final void startActionFor(@NotNull PsiElement element) {
    Project project = element.getProject();
    CompilerReferenceServiceImpl compilerReferenceService = (CompilerReferenceServiceImpl)CompilerReferenceService.getInstance(project);
    GlobalSearchScope scope = GlobalSearchScopeUtil.toGlobalSearchScope(element.getUseScope(), project);
    CompilerReferenceHierarchyTestInfo hierarchyTestInfo = getHierarchy(element, compilerReferenceService, scope);
    if (hierarchyTestInfo == null) {
      return;
    }
    InternalCompilerRefServiceView.showHierarchyInfo(hierarchyTestInfo, element);
  }

  protected abstract @Nullable CompilerReferenceHierarchyTestInfo getHierarchy(@NotNull PsiElement element,
                                                                               @NotNull CompilerReferenceServiceImpl refService,
                                                                               @NotNull GlobalSearchScope scope);
}
