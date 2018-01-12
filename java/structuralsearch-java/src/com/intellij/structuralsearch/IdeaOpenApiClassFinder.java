/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.structuralsearch;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.structuralsearch.plugin.util.StructuralSearchScriptScope;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IdeaOpenApiClassFinder extends NonClasspathClassFinder {
  public IdeaOpenApiClassFinder(@NotNull Project project) {
    super(project);
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    LocalFileSystem lfs = LocalFileSystem.getInstance();
    return Stream.of(PsiClass.class, PsiElement.class)
      .map(PathManager::getJarPathForClass)
      .filter(Objects::nonNull)
      .map(File::new)
      .map(lfs::findFileByIoFile)
      .collect(Collectors.toList());
  }

  @Override
  public PsiClass findClass(@NotNull String qualifiedName, @NotNull GlobalSearchScope scope) {
    return !(scope instanceof StructuralSearchScriptScope) ? null : super.findClass(qualifiedName, scope);
  }

  @NotNull
  @Override
  public PsiPackage[] getSubPackages(@NotNull PsiPackage psiPackage, @NotNull GlobalSearchScope scope) {
    return !(scope instanceof StructuralSearchScriptScope) ? PsiPackage.EMPTY_ARRAY : super.getSubPackages(psiPackage, scope);
  }
}
