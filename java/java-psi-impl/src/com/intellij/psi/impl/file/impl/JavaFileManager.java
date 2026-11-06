// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.function.Predicate;

public interface JavaFileManager {
  static JavaFileManager getInstance(@NotNull Project project) {
    return project.getService(JavaFileManager.class);
  }

  @Nullable
  PsiPackage findPackage(@NotNull String packageName);

  @Nullable
  PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope);

  PsiClass @NotNull [] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope);
  
  boolean hasClass(@NotNull String qName, @NotNull GlobalSearchScope scope, @NotNull Predicate<PsiClass> filter);

  @NotNull
  Collection<String> getNonTrivialPackagePrefixes();

  @NotNull
  Collection<PsiJavaModule> findModules(@NotNull String moduleName, @NotNull GlobalSearchScope scope);

  /**
   * Returns the package of {@code dir} from the classpath of {@link com.intellij.core.CoreJavaFileManager}.
   * Only {@link com.intellij.core.CoreJavaDirectoryService} calls this method.
   * Another file manager logs an error and gives {@code null}, because its directory service must find the package.
   */
  @ApiStatus.Internal
  default @Nullable PsiPackage getPackage(@NotNull PsiDirectory dir) {
    Logger.getInstance(JavaFileManager.class).error(
      "CoreJavaDirectoryService runs with " + getClass().getName() + ", which has no classpath of CoreJavaFileManager");
    return null;
  }
}