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
package com.intellij.psi;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.file.PsiPackageImpl;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.NonClasspathDirectoryScope;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author peter
 */
public abstract class NonClasspathClassFinder extends PsiElementFinder {
  protected final Project myProject;

  public NonClasspathClassFinder(Project project) {
    myProject = project;
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return null;
    }

    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final VirtualFile classFile = classRoot.findFileByRelativePath(qualifiedName.replace('.', '/') + ".class");
        if (classFile != null) {
          final PsiFile file = PsiManager.getInstance(myProject).findFile(classFile);
          if (file instanceof PsiClassOwner) {
            final PsiClass[] classes = ((PsiClassOwner)file).getClasses();
            if (classes.length == 1) {
              return classes[0];
            }
          }
        }
      }
    }
    return null;
  }

  protected abstract List<VirtualFile> getClassRoots();

  @NotNull
  @Override
  public PsiClass[] getClasses(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return PsiClass.EMPTY_ARRAY;
    }

    List<PsiClass> result = new ArrayList<PsiClass>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getQualifiedName();
        final VirtualFile dir = classRoot.findFileByRelativePath(pkgName.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (!file.isDirectory()) {
              final PsiFile psi = PsiManager.getInstance(myProject).findFile(file);
              if (psi instanceof PsiClassOwner) {
                result.addAll(Arrays.asList(((PsiClassOwner)psi).getClasses()));
              }
            }
          }
        }
      }
    }
    return result.toArray(new PsiClass[result.size()]);
  }

  @Override
  public PsiPackage findPackage(@NotNull String qualifiedName) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return null;
    }

    for (final VirtualFile classRoot : classRoots) {
      final VirtualFile dir = classRoot.findFileByRelativePath(qualifiedName.replace('.', '/'));
      if (dir != null && dir.isDirectory()) {
        return createPackage(qualifiedName);
      }
    }
    return null;
  }

  private PsiPackageImpl createPackage(String qualifiedName) {
    return new PsiPackageImpl((PsiManagerEx)PsiManager.getInstance(myProject), qualifiedName);
  }

  @Override
  public boolean processPackageDirectories(@NotNull PsiPackage psiPackage,
                                           @NotNull GlobalSearchScope scope,
                                           Processor<PsiDirectory> consumer) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return true;
    }

    final String qname = psiPackage.getQualifiedName();
    final PsiManager psiManager = psiPackage.getManager();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final VirtualFile dir = classRoot.findFileByRelativePath(qname.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          final PsiDirectory psiDirectory = ApplicationManager.getApplication().runReadAction(new Computable<PsiDirectory>() {
            public PsiDirectory compute() {
              return psiManager.findDirectory(dir);
            }
          });
          assert psiDirectory != null;
          if (!consumer.process(psiDirectory)) {
            return false;
          }
        }
      }
    }
    return true;
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    final List<VirtualFile> classRoots = getClassRoots();
    if (classRoots.isEmpty()) {
      return super.getSubPackages(psiPackage, scope);
    }

    List<PsiPackage> result = new ArrayList<PsiPackage>();
    for (final VirtualFile classRoot : classRoots) {
      if (scope.contains(classRoot)) {
        final String pkgName = psiPackage.getQualifiedName();
        final VirtualFile dir = classRoot.findFileByRelativePath(pkgName.replace('.', '/'));
        if (dir != null && dir.isDirectory()) {
          for (final VirtualFile file : dir.getChildren()) {
            if (file.isDirectory()) {
              result.add(createPackage(pkgName + "." + file.getName()));
            }
          }
        }
      }
    }
    return result.toArray(new PsiPackage[result.size()]);
  }

  @NotNull
  @Override
  public PsiClass[] findClasses(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    final PsiClass psiClass = findClass(qualifiedName, scope);
    return psiClass == null ? PsiClass.EMPTY_ARRAY : new PsiClass[]{psiClass};
  }

  public static GlobalSearchScope addNonClasspathScope(Project project, GlobalSearchScope base) {
    GlobalSearchScope scope = base;
    for (PsiElementFinder finder : Extensions.getExtensions(EP_NAME, project)) {
      if (finder instanceof NonClasspathClassFinder) {
        scope = scope.uniteWith(NonClasspathDirectoryScope.compose(((NonClasspathClassFinder)finder).getClassRoots()));
      }
    }
    return scope;
  }
}
