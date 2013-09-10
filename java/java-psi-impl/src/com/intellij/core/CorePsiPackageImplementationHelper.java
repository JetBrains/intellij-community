/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImplementationHelper;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author yole
 */
public class CorePsiPackageImplementationHelper extends PsiPackageImplementationHelper {
  private static final ModificationTracker[] EMPTY_DEPENDENCY = {ModificationTracker.NEVER_CHANGED};

  @Override
  public GlobalSearchScope adjustAllScope(PsiPackage psiPackage, GlobalSearchScope globalSearchScope) {
    return globalSearchScope;
  }

  @Override
  public VirtualFile[] occursInPackagePrefixes(PsiPackage psiPackage) {
    return VirtualFile.EMPTY_ARRAY;
  }

  @Override
  public void handleQualifiedNameChange(PsiPackage psiPackage, String newQualifiedName) {
  }

  @Override
  public void navigate(PsiPackage psiPackage, boolean requestFocus) {
  }

  @Override
  public boolean packagePrefixExists(PsiPackage psiPackage) {
    return false;
  }

  @Override
  public Object[] getDirectoryCachedValueDependencies(PsiPackage cachedValueProvider) {
    return EMPTY_DEPENDENCY;
  }
}
