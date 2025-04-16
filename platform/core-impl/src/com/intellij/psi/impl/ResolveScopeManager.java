// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


public abstract class ResolveScopeManager {
  public abstract @NotNull GlobalSearchScope getResolveScope(@NotNull PsiElement element);

  public abstract @NotNull GlobalSearchScope getDefaultResolveScope(@NotNull VirtualFile vFile);

  public abstract @NotNull GlobalSearchScope getUseScope(@NotNull PsiElement element);

  public static @NotNull ResolveScopeManager getInstance(@NotNull Project project) {
    return project.getService(ResolveScopeManager.class);
  }

  public static @NotNull GlobalSearchScope getElementUseScope(@NotNull PsiElement element) {
    return getInstance(element.getProject()).getUseScope(element);
  }

  public static @NotNull GlobalSearchScope getElementResolveScope(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file != null) {
      return getInstance(file.getProject()).getResolveScope(file);
    }
    return getInstance(element.getProject()).getResolveScope(element);
  }

  /**
   * Marks a specified file to use a weak file scope. This is useful for modifying
   * the scoping mechanism of a file to ensure it is treated under weaker constraints
   * than the default resolve scope. It can be used to improve memory consumption.
   *
   * @param file the virtual file to be marked for weak scope; must not be null
   */
  @ApiStatus.Experimental
  @ApiStatus.Internal
  public void markFileForWeakScope(@NotNull VirtualFile file){}
}
