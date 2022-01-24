// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.model.ModelBranch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 *  @author dsl
 */
public class SingleSourceRootMoveDestination implements MoveDestination {
  private static final Logger LOG = Logger.getInstance(SingleSourceRootMoveDestination.class);
  @NotNull
  private final PackageWrapper myPackage;
  @NotNull
  private final PsiDirectory myTargetDirectory;

  public SingleSourceRootMoveDestination(@NotNull PackageWrapper aPackage, @NotNull PsiDirectory targetDirectory) {
    LOG.assertTrue(aPackage.equalToPackage(JavaDirectoryService.getInstance().getPackage(targetDirectory)));
    myPackage = aPackage;
    myTargetDirectory = targetDirectory;
  }

  @NotNull
  @Override
  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  @Override
  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return myTargetDirectory;
  }

  @Override
  public PsiDirectory getTargetIfExists(@NotNull PsiFile source) {
    return myTargetDirectory;
  }

  @Override
  public PsiDirectory getTargetDirectory(PsiDirectory source) {
    return getDirectory(source);
  }

  @Override
  public String verify(PsiFile source) {
    return null;
  }

  @Override
  public String verify(PsiDirectory source) {
    return null;
  }

  @Override
  public String verify(PsiPackage source) {
    return null;
  }

  @Override
  public void analyzeModuleConflicts(@NotNull final Collection<? extends PsiElement> elements,
                                     @NotNull MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages) {
    JavaSpecialRefactoringProvider.getInstance()
      .analyzeModuleConflicts(myPackage.getManager().getProject(), elements, usages, myTargetDirectory, conflicts);
  }

  @Override
  public boolean isTargetAccessible(@NotNull Project project, @NotNull VirtualFile place) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
    final Module module = ModuleUtilCore.findModuleForFile(place, project);
    final VirtualFile targetVirtualFile = myTargetDirectory.getVirtualFile();
    if (module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetVirtualFile)) {
      return false;
    }
    return true;
  }

  @Override
  public PsiDirectory getTargetDirectory(PsiFile source) {
    return getDirectory(source);
  }

  private PsiDirectory getDirectory(PsiElement source) {
    ModelBranch branch = ModelBranch.getPsiBranch(source);
    return branch == null ? myTargetDirectory : branch.obtainPsiCopy(myTargetDirectory);
  }
}
