// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;


public class CorePsiPackageImplementationHelper extends PsiPackageImplementationHelper {
  private static final ModificationTracker[] EMPTY_DEPENDENCY = {ModificationTracker.NEVER_CHANGED};

  @NotNull
  @Override
  public GlobalSearchScope adjustAllScope(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope globalSearchScope) {
    return globalSearchScope;
  }

  @Override
  public VirtualFile @NotNull [] occursInPackagePrefixes(@NotNull PsiPackage psiPackage) {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public void handleQualifiedNameChange(@NotNull PsiPackage psiPackage, @NotNull String newQualifiedName) {
  }

  @Override
  public void navigate(@NotNull PsiPackage psiPackage, boolean requestFocus) {
  }

  @Override
  public boolean packagePrefixExists(@NotNull PsiPackage psiPackage) {
    return false;
  }

  @Override
  public Object @NotNull [] getDirectoryCachedValueDependencies(@NotNull PsiPackage cachedValueProvider) {
    return EMPTY_DEPENDENCY;
  }
}
