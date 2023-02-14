// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;


public class PackagePrefixElementFinder extends PsiElementFinder implements DumbAware {
  private final Project myProject;
  private final PackagePrefixIndex myPackagePrefixIndex;

  public PackagePrefixElementFinder(Project project) {
    myProject = project;
    myPackagePrefixIndex = new PackagePrefixIndex(project);
  }

  @NotNull
  public static PackagePrefixElementFinder getInstance(@NotNull Project project) {
    return PsiElementFinder.EP.findExtensionOrFail(PackagePrefixElementFinder.class, project);
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return null;
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    if (packagePrefixExists(qualifiedName)) {
      return new PsiPackageImpl(PsiManager.getInstance(myProject), qualifiedName);
    }
    return null;
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
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
    return packagesMap.values().toArray(PsiPackage.EMPTY_ARRAY);
  }

  @Contract(pure = true)
  public boolean packagePrefixExists(String packageQName) {
    for (final String prefix : myPackagePrefixIndex.getAllPackagePrefixes(null)) {
      if (StringUtil.startsWithConcatenation(prefix, packageQName, ".") || prefix.equals(packageQName)) {
        return true;
      }
    }

    return false;
  }
  
   public Collection<String> getAllPackagePrefixes(GlobalSearchScope scope) {
    return myPackagePrefixIndex.getAllPackagePrefixes(scope);
  }

}
