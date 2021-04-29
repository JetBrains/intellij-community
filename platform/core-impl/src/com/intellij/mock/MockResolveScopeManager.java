// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.mock;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;


public class MockResolveScopeManager extends ResolveScopeManager {
  private final Project myProject;

  public MockResolveScopeManager(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    return GlobalSearchScope.allScope(element.getProject());
  }

  @NotNull
  @Override
  public GlobalSearchScope getDefaultResolveScope(@NotNull VirtualFile vFile) {
    return GlobalSearchScope.allScope(myProject);
  }

  @NotNull
  @Override
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    return GlobalSearchScope.allScope(element.getProject());
  }
}
