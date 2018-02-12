// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl;

import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.file.impl.JavaFileManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubTreeLoader;
import com.intellij.psi.util.PsiClassUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author kosyakov
 * @since 05.12.2014
 */
public class PsiElementFinderImpl extends PsiElementFinder implements DumbAware {
  private final Project myProject;
  private final JavaFileManager myFileManager;

  public PsiElementFinderImpl(Project project, JavaFileManager javaFileManager) {
    myProject = project;
    myFileManager = javaFileManager;
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return myFileManager.findClass(qualifiedName, scope);
  }

  @Override
  @NotNull
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return myFileManager.findClasses(qualifiedName, scope);
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    return myFileManager.findPackage(qualifiedName);
  }

  @Override
  @NotNull
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final Map<String, PsiPackage> packagesMap = new HashMap<>();
    final String qualifiedName = psiPackage.getQualifiedName();
    for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
      PsiDirectory[] subDirs = dir.getSubdirectories();
      for (PsiDirectory subDir : subDirs) {
        final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(subDir);
        if (aPackage != null) {
          final String subQualifiedName = aPackage.getQualifiedName();
          if (subQualifiedName.startsWith(qualifiedName) && !packagesMap.containsKey(subQualifiedName)) {
            packagesMap.put(aPackage.getQualifiedName(), aPackage);
          }
        }
      }
    }

    packagesMap.remove(qualifiedName);    // avoid SOE caused by returning a package as a subpackage of itself
    return packagesMap.values().toArray(PsiPackage.EMPTY_ARRAY);
  }

  @Override
  @NotNull
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull final GlobalSearchScope scope) {
    return getClasses(null, psiPackage, scope);
  }

  @Override
  @NotNull
  public PsiClass[] getClasses(@Nullable String shortName, @NotNull PsiPackage psiPackage, @NotNull final GlobalSearchScope scope) {
    List<PsiClass> list = null;
    String packageName = psiPackage.getQualifiedName();
    for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
      PsiClass[] classes = JavaDirectoryService.getInstance().getClasses(dir);
      if (classes.length == 0) continue;
      if (list == null) list = new ArrayList<>();
      for (PsiClass aClass : classes) {
        // class file can be located in wrong place inside file system
        String qualifiedName = aClass.getQualifiedName();
        if (qualifiedName != null) qualifiedName = StringUtil.getPackageName(qualifiedName);
        if (Comparing.strEqual(qualifiedName, packageName)) {
          if (shortName == null || shortName.equals(aClass.getName())) list.add(aClass);
        }
      }
    }
    if (list == null) {
      return PsiClass.EMPTY_ARRAY;
    }

    if (list.size() > 1) {
      ContainerUtil.quickSort(list, PsiClassUtil.createScopeComparator(scope));
    }

    return list.toArray(PsiClass.EMPTY_ARRAY);
  }

  @NotNull
  @Override
  public Set<String> getClassNames(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    Set<String> names = null;
    FileIndexFacade facade = FileIndexFacade.getInstance(myProject);
    for (PsiDirectory dir : psiPackage.getDirectories(scope)) {
      for (PsiFile file : dir.getFiles()) {
        if (file instanceof PsiClassOwner && file.getViewProvider().getLanguages().size() == 1) {
          VirtualFile vFile = file.getVirtualFile();
          if (vFile != null &&
              !(file instanceof PsiCompiledElement) &&
              !facade.isInSourceContent(vFile) &&
              (!scope.isForceSearchingInLibrarySources() || !StubTreeLoader.getInstance().canHaveStub(vFile))) {
            continue;
          }

          Set<String> inFile =
            file instanceof PsiClassOwnerEx ? ((PsiClassOwnerEx)file).getClassNames() : getClassNames(((PsiClassOwner)file).getClasses());

          if (inFile.isEmpty()) continue;
          if (names == null) names = new HashSet<>();
          names.addAll(inFile);
        }
      }

    }
    return names == null ? Collections.emptySet() : names;
  }

  @Override
  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull final GlobalSearchScope scope,
                                           @NotNull final Processor<PsiDirectory> consumer,
                                           boolean includeLibrarySources) {
    final PsiManager psiManager = PsiManager.getInstance(myProject);
    return PackageIndex.getInstance(myProject)
      .getDirsByPackageName(psiPackage.getQualifiedName(), includeLibrarySources)
      .forEach(new ReadActionProcessor<VirtualFile>() {
        @Override
        public boolean processInReadAction(final VirtualFile dir) {
          if (!scope.contains(dir)) return true;
          PsiDirectory psiDir = psiManager.findDirectory(dir);
          return psiDir == null || consumer.process(psiDir);
        }
      });
  }
}
