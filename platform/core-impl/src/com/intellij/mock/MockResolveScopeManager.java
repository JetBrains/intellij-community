// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


@ApiStatus.Internal
public class MockResolveScopeManager extends ResolveScopeManager {
  private final Project myProject;

  public MockResolveScopeManager(Project project) {
    myProject = project;
  }

  @Override
  public @NotNull GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    return GlobalSearchScope.allScope(element.getProject());
  }

  @Override
  public @NotNull GlobalSearchScope getDefaultResolveScope(@NotNull VirtualFile vFile) {
    return GlobalSearchScope.allScope(myProject);
  }

  @Override
  public @NotNull GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    return GlobalSearchScope.allScope(element.getProject());
  }
}
