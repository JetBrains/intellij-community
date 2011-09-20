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
package com.intellij.refactoring.move.moveClassesOrPackages;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;

import java.util.Collection;

/**
 *  @author dsl
 */
public class SingleSourceRootMoveDestination implements MoveDestination {
  private static final Logger LOG = Logger.getInstance(
    "#com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination");
  private final PackageWrapper myPackage;
  private final PsiDirectory myTargetDirectory;

  public SingleSourceRootMoveDestination(PackageWrapper aPackage, PsiDirectory targetDirectory) {
    LOG.assertTrue(aPackage.equalToPackage(JavaDirectoryService.getInstance().getPackage(targetDirectory)));
    myPackage = aPackage;
    myTargetDirectory = targetDirectory;
  }

  public PackageWrapper getTargetPackage() {
    return myPackage;
  }

  public PsiDirectory getTargetIfExists(PsiDirectory source) {
    return myTargetDirectory;
  }

  public PsiDirectory getTargetIfExists(PsiFile source) {
    return myTargetDirectory;
  }

  public PsiDirectory getTargetDirectory(PsiDirectory source) {
    return myTargetDirectory;
  }

  public String verify(PsiFile source) {
    return null;
  }

  public String verify(PsiDirectory source) {
    return null;
  }

  public String verify(PsiPackage source) {
    return null;
  }

  public void analyzeModuleConflicts(final Collection<PsiElement> elements,
                                     MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages) {
    RefactoringConflictsUtil.analyzeModuleConflicts(myPackage.getManager().getProject(), elements, usages, myTargetDirectory, conflicts);
  }

  @Override
  public boolean isTargetAccessible(Project project, VirtualFile place) {
    final boolean inTestSourceContent = ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(place);
    final Module module = ModuleUtil.findModuleForFile(place, project);
    final VirtualFile targetVirtualFile = myTargetDirectory.getVirtualFile();
    if (module != null &&
        !GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, inTestSourceContent).contains(targetVirtualFile)) {
      return false;
    }
    return true;
  }

  public PsiDirectory getTargetDirectory(PsiFile source) {
    return myTargetDirectory;
  }
}
