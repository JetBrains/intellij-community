// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;


public interface PackageDirectoryProvider {
  ProjectExtensionPointName<PackageDirectoryProvider> EP_NAME =
    new ProjectExtensionPointName<>("com.intellij.packageDirectoryProvider");

  /**
   * Provides directories that correspond to a {@link PsiPackage}.
   *
   * @param psiPackage target package for which directories are requested
   * @param scope the search scope within which directories are sought
   * @param consumer the processor to which directories are passed
   * @param includeLibrarySources whether to include library source directories
   *
   * @return {@code false} if the consumer requested early termination, {@code true} otherwise.
   */
  boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                    @NotNull GlobalSearchScope scope,
                                    @NotNull Processor<? super PsiDirectory> consumer,
                                    boolean includeLibrarySources);
}
