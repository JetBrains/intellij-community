// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public interface JavaFileManager {
  static JavaFileManager getInstance(@NotNull Project project) {
    return project.getService(JavaFileManager.class);
  }

  @Nullable
  PsiPackage findPackage(@NotNull String packageName);

  @Nullable
  PsiClass findClass(@NotNull String qName, @NotNull GlobalSearchScope scope);

  PsiClass @NotNull [] findClasses(@NotNull String qName, @NotNull GlobalSearchScope scope);

  @NotNull
  Collection<String> getNonTrivialPackagePrefixes();

  @NotNull
  Collection<PsiJavaModule> findModules(@NotNull String moduleName, @NotNull GlobalSearchScope scope);
}