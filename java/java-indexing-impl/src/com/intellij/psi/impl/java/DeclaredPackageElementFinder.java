// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFinder;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.Processor;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds a package which a {@code package} statement declares, even when no directory corresponds to it.
 * <p>
 * {@link com.intellij.psi.impl.PsiElementFinderImpl} answers from the directory structure and from the package prefix of a source
 * root, so it cannot find such a package. This finder answers from {@link JavaDeclaredPackageIndex} instead. It reports no class of
 * its own: a class already resolves by its qualified name through
 * {@link com.intellij.psi.impl.java.stubs.index.JavaFullClassNameIndex}, and {@code PsiElementFinderImpl} collects the classes of the
 * files which {@link #processPackageFiles} reports.
 * <p>
 * This finder is not dumb aware, because it needs the index.
 */
@ApiStatus.Internal
public final class DeclaredPackageElementFinder extends PsiElementFinder {
  private final Project myProject;

  public DeclaredPackageElementFinder(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @Nullable PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return null;
  }

  @Override
  public PsiClass @NotNull [] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return PsiClass.EMPTY_ARRAY;
  }

  @Override
  public @Nullable PsiPackage findPackage(@NotNull String qualifiedName) {
    if (!packageExistenceCache().get(qualifiedName)) return null;
    return new PsiPackageImpl(PsiManager.getInstance(myProject), qualifiedName);
  }

  @Override
  public PsiPackage @NotNull [] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    String qualifiedName = psiPackage.getQualifiedName();
    Set<String> subPackageNames = JavaDeclaredPackageIndex.getSubPackageNames(qualifiedName, scope);
    if (subPackageNames.isEmpty()) return PsiPackage.EMPTY_ARRAY;

    PsiManager manager = psiPackage.getManager();
    List<PsiPackage> result = new ArrayList<>(subPackageNames.size());
    for (String subPackageName : subPackageNames) {
      result.add(new PsiPackageImpl(manager, qualifiedName.isEmpty() ? subPackageName : qualifiedName + "." + subPackageName));
    }
    return result.toArray(PsiPackage.EMPTY_ARRAY);
  }

  @Override
  public boolean processPackageFiles(@NotNull PsiPackage psiPackage,
                                     @NotNull GlobalSearchScope scope,
                                     @NotNull Processor<? super VirtualFile> consumer) {
    String qualifiedName = psiPackage.getQualifiedName();
    PackageIndex packageIndex = PackageIndex.getInstance(myProject);
    for (VirtualFile file : JavaDeclaredPackageIndex.getFilesWithExactPackage(qualifiedName, scope)) {
      // a directory of the package already reports this file, and PsiElementFinderImpl collects its classes through the directory
      if (qualifiedName.equals(packageIndex.getPackageName(file))) continue;
      if (!consumer.process(file)) return false;
    }
    return true;
  }

  /**
   * {@link com.intellij.psi.impl.JavaPsiFacadeImpl#findPackage} caches a hit but not a miss, and every unresolved reference asks
   * again, so this finder caches both.
   */
  private @NotNull Map<String, Boolean> packageExistenceCache() {
    return CachedValuesManager.getManager(myProject).getCachedValue(myProject, () -> CachedValueProvider.Result.create(
      ConcurrentFactoryMap.createMap(
        qualifiedName -> JavaDeclaredPackageIndex.packageExists(qualifiedName, GlobalSearchScope.allScope(myProject))),
      PsiModificationTracker.MODIFICATION_COUNT, ProjectRootManager.getInstance(myProject)));
  }
}
