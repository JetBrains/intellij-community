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
package com.intellij.psi.impl;

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author yole
 */
public class PackagePrefixElementFinder extends PsiElementFinder implements DumbAware {
  private final Project myProject;
  private final PackagePrefixIndex myPackagePrefixIndex;

  public PackagePrefixElementFinder(Project project) {
    myProject = project;
    myPackagePrefixIndex = new PackagePrefixIndex(project);
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return null;
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    if (packagePrefixExists(qualifiedName)) {
      return new PsiPackageImpl(PsiManager.getInstance(myProject), qualifiedName);
    }
    return null;
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final Map<String, PsiPackage> packagesMap = new HashMap<>();
    final String qualifiedName = psiPackage.getQualifiedName();

    for (final String prefix : myPackagePrefixIndex.getAllPackagePrefixes(scope)) {
      if (StringUtil.isEmpty(qualifiedName) || StringUtil.startsWithConcatenation(prefix, qualifiedName, ".")) {
        final int i = prefix.indexOf('.', qualifiedName.length() + 1);
        String childName = i >= 0 ? prefix.substring(0, i) : prefix;
        if (!packagesMap.containsKey(childName)) {
          packagesMap.put(childName, new PsiPackageImpl(psiPackage.getManager(), childName));
        }
      }
    }

    packagesMap.remove(qualifiedName);    // avoid SOE caused by returning a package as a subpackage of itself
    return packagesMap.values().toArray(new PsiPackage[packagesMap.size()]);
  }

  public boolean packagePrefixExists(String packageQName) {
    for (final String prefix : myPackagePrefixIndex.getAllPackagePrefixes(null)) {
      if (StringUtil.startsWithConcatenation(prefix, packageQName, ".") || prefix.equals(packageQName)) {
        return true;
      }
    }

    return false;
  }

  public static PackagePrefixElementFinder getInstance(Project project) {
    for (PsiElementFinder o : Extensions.getExtensions(PsiElementFinder.EP_NAME, project)) {
      if (o instanceof PackagePrefixElementFinder) {
        return (PackagePrefixElementFinder) o;
      }
    }
    throw new UnsupportedOperationException("couldn't find self");
  }
}
