/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.file;

import com.intellij.openapi.components.ServiceManager;
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

  @NotNull
  public abstract VirtualFile[] occursInPackagePrefixes(@NotNull PsiPackage psiPackage);

  public abstract void handleQualifiedNameChange(@NotNull PsiPackage psiPackage, @NotNull String newQualifiedName);

  public abstract void navigate(@NotNull PsiPackage psiPackage, boolean requestFocus);

  public abstract boolean packagePrefixExists(@NotNull PsiPackage psiPackage);

  @NotNull
  public abstract Object[] getDirectoryCachedValueDependencies(@NotNull PsiPackage cachedValueProvider);

  public static PsiPackageImplementationHelper getInstance() {
    return ServiceManager.getService(PsiPackageImplementationHelper.class);
  }
}
