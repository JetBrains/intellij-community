/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public class PackageScope extends GlobalSearchScope {
  private final Collection<VirtualFile> myDirs;
  private final PsiPackage myPackage;
  private final boolean myIncludeSubpackages;
  private final boolean myIncludeLibraries;

  public PackageScope(@NotNull PsiPackage aPackage, boolean includeSubpackages, final boolean includeLibraries) {
    super(aPackage.getProject());
    myPackage = aPackage;
    myIncludeSubpackages = includeSubpackages;

    Project project = myPackage.getProject();
    myDirs = PackageIndex.getInstance(project).getDirsByPackageName(myPackage.getQualifiedName(), true).findAll();
    myIncludeLibraries = includeLibraries;
  }

  public boolean contains(VirtualFile file) {
    for (VirtualFile scopeDir : myDirs) {
      boolean inDir = myIncludeSubpackages
                      ? VfsUtilCore.isAncestor(scopeDir, file, false)
                      : Comparing.equal(file.getParent(), scopeDir);
      if (inDir) return true;
    }
    return false;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    return 0;
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  public boolean isSearchInLibraries() {
    return myIncludeLibraries;
  }

  public String toString() {
    //noinspection HardCodedStringLiteral
    return "package scope: " + myPackage +
           ", includeSubpackages = " + myIncludeSubpackages;
  }

  public static GlobalSearchScope packageScope(@NotNull PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, true);
  }

  public static GlobalSearchScope packageScopeWithoutLibraries(@NotNull PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, false);
  }
}