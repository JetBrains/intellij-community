// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class PackageScope extends GlobalSearchScope {
  private final Set<VirtualFile> myDirs;
  private final Set<VirtualFile> myFiles;
  private final PsiPackage myPackage;
  private final boolean myIncludeSubpackages;
  private final boolean myIncludeLibraries;
  private final boolean myPartOfPackagePrefix;
  private final String myPackageQualifiedName;
  private final String myPackageQNamePrefix;

  public PackageScope(@NotNull PsiPackage aPackage, boolean includeSubpackages, final boolean includeLibraries) {
    this(aPackage, includeSubpackages, includeLibraries, null);
  }

  private PackageScope(@NotNull PsiPackage aPackage,
                       boolean includeSubpackages,
                       final boolean includeLibraries,
                       @Nullable GlobalSearchScope packageScope) {
    super(aPackage.getProject());
    myPackage = aPackage;
    myIncludeSubpackages = includeSubpackages;

    Project project = myPackage.getProject();
    myPackageQualifiedName = myPackage.getQualifiedName();

    PackageIndex packageIndex = PackageIndex.getInstance(project);
    Query<VirtualFile> dirs = packageScope != null
                              ? packageIndex.getDirsByPackageName(myPackageQualifiedName, packageScope)
                              : packageIndex.getDirsByPackageName(myPackageQualifiedName, true);

    myDirs = VfsUtilCore.createCompactVirtualFileSet();
    dirs.forEach(e -> {
      myDirs.add(e);
      return true;
    });
    
    Query<VirtualFile> files = packageIndex.getFilesByPackageName(myPackageQualifiedName);
    if (packageScope != null) {
      files = files.filtering(packageScope::contains);
    }
    myFiles = VfsUtilCore.createCompactVirtualFileSet();
    files.forEach(e -> {
      myFiles.add(e);
      return true;
    });

    myIncludeLibraries = includeLibraries;

    myPartOfPackagePrefix = JavaPsiFacade.getInstance(project).isPartOfPackagePrefix(myPackageQualifiedName);
    myPackageQNamePrefix = myPackageQualifiedName + ".";
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (!myIncludeSubpackages) {
      if (myDirs.contains(dir)) return true;
    }
    else {
      while (dir != null) {
        if (myDirs.contains(dir)) return true;
        dir = dir.getParent();
      }
    }

    if (myPartOfPackagePrefix && myIncludeSubpackages) {
      final PsiFile psiFile = myPackage.getManager().findFile(file);
      if (psiFile instanceof PsiClassOwner) {
        final String packageName = ((PsiClassOwner)psiFile).getPackageName();
        if (myPackageQualifiedName.equals(packageName) ||
            packageName.startsWith(myPackageQNamePrefix)) {
          return true;
        }
      }
    }
    return myFiles.contains(file);
  }

  @Override
  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return true;
  }

  @Override
  public boolean isSearchInLibraries() {
    return myIncludeLibraries;
  }

  @Override
  public String toString() {
    return "package scope: " + myPackage +
           ", includeSubpackages = " + myIncludeSubpackages;
  }

  public static @NotNull GlobalSearchScope packageScope(@NotNull PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, true);
  }

  public static @NotNull GlobalSearchScope packageScope(@NotNull PsiPackage aPackage,
                                                        boolean includeSubpackages,
                                                        @NotNull GlobalSearchScope packageScope) {
    return new PackageScope(aPackage, includeSubpackages, true, packageScope);
  }

  public static @NotNull GlobalSearchScope packageScopeWithoutLibraries(@NotNull PsiPackage aPackage, boolean includeSubpackages) {
    return new PackageScope(aPackage, includeSubpackages, false);
  }
}