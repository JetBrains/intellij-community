// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class PsiPackageImplementationHelper {
  @NotNull
  public abstract GlobalSearchScope adjustAllScope(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope globalSearchScope);

  public abstract VirtualFile @NotNull [] occursInPackagePrefixes(@NotNull PsiPackage psiPackage);

  public abstract void handleQualifiedNameChange(@NotNull PsiPackage psiPackage, @NotNull String newQualifiedName);

  public abstract void navigate(@NotNull PsiPackage psiPackage, boolean requestFocus);

  public abstract boolean packagePrefixExists(@NotNull PsiPackage psiPackage);

  public abstract Object @NotNull [] getDirectoryCachedValueDependencies(@NotNull PsiPackage cachedValueProvider);

  public static PsiPackageImplementationHelper getInstance() {
    return ApplicationManager.getApplication().getService(PsiPackageImplementationHelper.class);
  }
}
