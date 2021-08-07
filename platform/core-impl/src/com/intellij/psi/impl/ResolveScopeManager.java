// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;


public abstract class ResolveScopeManager {
  @NotNull
  public abstract GlobalSearchScope getResolveScope(@NotNull PsiElement element);

  @NotNull
  public abstract GlobalSearchScope getDefaultResolveScope(@NotNull VirtualFile vFile);

  @NotNull
  public abstract GlobalSearchScope getUseScope(@NotNull PsiElement element);

  @NotNull
  public static ResolveScopeManager getInstance(@NotNull Project project) {
    return project.getService(ResolveScopeManager.class);
  }

  @NotNull
  public static GlobalSearchScope getElementUseScope(@NotNull PsiElement element) {
    return getInstance(element.getProject()).getUseScope(element);
  }

  @NotNull
  public static GlobalSearchScope getElementResolveScope(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      return getInstance(file.getProject()).getResolveScope(file);
    }
    return getInstance(element.getProject()).getResolveScope(element);
  }
}
