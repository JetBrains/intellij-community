// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.model.ModelBranch;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 *  @author dsl
 */
public class AutocreatingSingleSourceRootMoveDestination extends AutocreatingMoveDestination {
  private final VirtualFile mySourceRoot;

  public AutocreatingSingleSourceRootMoveDestination(PackageWrapper targetPackage, @NotNull VirtualFile sourceRoot) {
    super(targetPackage);
    mySourceRoot = sourceRoot;
  }

  @NotNull
  @Override
  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  @Override
  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  @Override
  public PsiDirectory getTargetIfExists(@NotNull PsiFile source) {
    return CommonJavaRefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  @Override
  public PsiDirectory getTargetDirectory(@Nullable PsiDirectory source) throws IncorrectOperationException {
    return getDirectory(source);
  }

  @Override
  public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
    return getDirectory(source);
  }

  @Override
  @Nullable
  public String verify(PsiFile source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  @Override
  public String verify(PsiDirectory source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  @Override
  public String verify(PsiPackage aPackage) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  @Override
  public void analyzeModuleConflicts(@NotNull final Collection<? extends PsiElement> elements,
                                     @NotNull MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages) {
    JavaSpecialRefactoringProvider.getInstance()
      .analyzeModuleConflicts(getTargetPackage().getManager().getProject(), elements, usages, mySourceRoot, conflicts);
  }

  @Override
  public boolean isTargetAccessible(@NotNull Project project, @NotNull VirtualFile place) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
    final Module module = ModuleUtilCore.findModuleForFile(place, project);
    if (mySourceRoot != null &&
        module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(mySourceRoot)) {
      return false;
    }
    return true;
  }

  PsiDirectory myTargetDirectory;
  private PsiDirectory getDirectory(@Nullable PsiElement source) throws IncorrectOperationException {
    if (myTargetDirectory == null) {
      VirtualFile sourceRoot = mySourceRoot;
      ModelBranch branch = source == null ? null : ModelBranch.getPsiBranch(source);
      if (branch != null) {
        sourceRoot = branch.findFileCopy(mySourceRoot);
      }
      myTargetDirectory = CommonJavaRefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, sourceRoot);
    }
    return myTargetDirectory;
  }
}
