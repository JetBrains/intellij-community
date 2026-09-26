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
  private final Set<String> myDirUrls;
  private final Set<VirtualFile> myFiles;
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
    myDirUrls = urlsOf(myDirs);

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
    // With includeSubpackages such a file can also declare a subpackage, and then no directory of myDirs is an ancestor of it either.
    GlobalSearchScope effectiveScope = packageScope != null ? packageScope : allScope(project);
    for (PsiFile file : myPackage.getIndividualFiles(effectiveScope, includeSubpackages)) {
      VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile != null) {
        myFiles.add(virtualFile);
      }
    }
    myFileUrls = urlsOf(myFiles);

    myIncludeLibraries = includeLibraries;

    myPartOfPackagePrefix = JavaPsiFacade.getInstance(project).isPartOfPackagePrefix(myPackageQualifiedName);
    myPackageQNamePrefix = myPackageQualifiedName + ".";
  }

  private static @NotNull Set<String> urlsOf(@NotNull Set<VirtualFile> files) {
    return files.isEmpty() ? Collections.emptySet() : new HashSet<>(ContainerUtil.map(files, VirtualFile::getUrl));
  }

  /**
   * The same check as the one over {@link #myDirs}, but by URL, for a {@link VirtualFile} instance which the sets do not hold.
   */
  private boolean containsDirByUrl(@Nullable VirtualFile dir) {
    if (dir == null || myDirUrls.isEmpty()) return false;
    String url = dir.getUrl();
    return myIncludeSubpackages ? VfsUtilCore.isUnder(url, myDirUrls) : myDirUrls.contains(url);
  }

  @Override
  public boolean contains(@NotNull VirtualFile file) {
    VirtualFile fileDir = file.isDirectory() ? file : file.getParent();
    if (!myIncludeSubpackages) {
      if (myDirs.contains(fileDir)) return true;
    }
    else {
      VirtualFile dir = fileDir;
      while (dir != null) {
        if (myDirs.contains(dir)) return true;
        dir = dir.getParent();
      }
    }
    if (containsDirByUrl(fileDir)) return true;

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