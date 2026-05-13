// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PackageDirectoryProvider;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.JavaPsiFacadeImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public final class JavaPackageDirectoryProvider implements PackageDirectoryProvider {
  @Override
  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           @NotNull Processor<? super PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    return ((JavaPsiFacadeImpl)JavaPsiFacade.getInstance(psiPackage.getProject()))
      .processPackageDirectories(psiPackage, scope, consumer, includeLibrarySources);
  }
}
