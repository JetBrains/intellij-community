/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
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

  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  public PsiDirectory getTargetIfExists(PsiFile source) {
    return RefactoringUtil.findPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
  }

  public PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException {
    return getDirectory();
  }

  public PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException {
    return getDirectory();
  }

  @Nullable
  public String verify(PsiFile source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  public String verify(PsiDirectory source) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  public String verify(PsiPackage aPackage) {
    return checkCanCreateInSourceRoot(mySourceRoot);
  }

  public void analyzeModuleConflicts(final Collection<PsiElement> elements,
                                     MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages) {
    RefactoringConflictsUtil.analyzeModuleConflicts(getTargetPackage().getManager().getProject(), elements, usages, mySourceRoot, conflicts);
  }

  @Override
  public boolean isTargetAccessible(Project project, VirtualFile place) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
    final Module module = ModuleUtil.findModuleForFile(place, project);
    if (mySourceRoot != null &&
        module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(mySourceRoot)) {
      return false;
    }
    return true;
  }

  PsiDirectory myTargetDirectory;
  private PsiDirectory getDirectory() throws IncorrectOperationException {
    if (myTargetDirectory == null) {
      myTargetDirectory = WriteAction.compute(() -> {
        try {
          return RefactoringUtil.createPackageDirectoryInSourceRoot(myPackage, mySourceRoot);
        }
        catch (IncorrectOperationException e) {
          return null;
        }
      });
    }
    return myTargetDirectory;
  }
}
