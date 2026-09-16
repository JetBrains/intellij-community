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
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class PackageScope extends GlobalSearchScope {
  private final Set<VirtualFile> myDirs;
  private final Set<VirtualFile> myFiles;
  /**
   * The URL of each file of {@link #myFiles}. An environment can represent one file by more than one
   * {@link VirtualFile} instance, and {@link #myFiles} then misses the instance which the caller holds.
   */
  private final Set<String> myFileUrls;
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

    // A file can declare a package which no directory holds. PackageIndex does not know such a file, but an element finder does.
    for (PsiFile file : myPackage.getIndividualFiles(packageScope != null ? packageScope : allScope(project))) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myFiles.add(virtualFile);
      }
    }
    myFileUrls = myFiles.isEmpty() ? Collections.emptySet() : new HashSet<>(ContainerUtil.map(myFiles, VirtualFile::getUrl));

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
    if (myFiles.contains(file)) return true;
    return !myFileUrls.isEmpty() && myFileUrls.contains(file.getUrl());
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