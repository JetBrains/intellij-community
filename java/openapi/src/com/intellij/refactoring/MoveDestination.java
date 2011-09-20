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
package com.intellij.refactoring;

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
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Represents a destination of Move Classes/Packages refactoring.
 * Destination of Move refactoring is generally a single package,
 * and various <code>MoveDestination</code>s control how moved items
 * will be layouted in directories corresponding to target packages.
 *
 * Instances of this interface can be obtained via methods of {@link RefactoringFactory}.
 *
 * @see JavaRefactoringFactory#createSourceFolderPreservingMoveDestination(String) 
 * @see JavaRefactoringFactory#createSourceRootMoveDestination(java.lang.String, com.intellij.openapi.vfs.VirtualFile)
 *  @author dsl
 */
public interface MoveDestination {
  /**
   * Invoked in command & write action
   */
  PsiDirectory getTargetDirectory(PsiDirectory source) throws IncorrectOperationException;
  /**
   * Invoked in command & write action
   */
  PsiDirectory getTargetDirectory(PsiFile source) throws IncorrectOperationException;

  PackageWrapper getTargetPackage();

  PsiDirectory getTargetIfExists(PsiDirectory source);
  PsiDirectory getTargetIfExists(PsiFile source);

  @Nullable
  String verify(PsiFile source);
  @Nullable
  String verify(PsiDirectory source);
  @Nullable
  String verify(PsiPackage source);

  void analyzeModuleConflicts(final Collection<PsiElement> elements, MultiMap<PsiElement,String> conflicts, final UsageInfo[] usages);

  boolean isTargetAccessible(Project project, VirtualFile place);
}
