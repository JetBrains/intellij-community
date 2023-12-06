// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Represents a destination of Move Classes/Packages refactoring.
 * Destination of Move refactoring is generally a single package,
 * and various {@code MoveDestination}s control how moved items
 * will be layouted in directories corresponding to target packages.
 *
 * Instances of this interface can be obtained via methods of {@link RefactoringFactory}.
 *
 * @see JavaRefactoringFactory#createSourceFolderPreservingMoveDestination(String) 
 * @see JavaRefactoringFactory#createSourceRootMoveDestination(String, VirtualFile)
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

  @NotNull
  PackageWrapper getTargetPackage();

  PsiDirectory getTargetIfExists(PsiDirectory source);
  PsiDirectory getTargetIfExists(@NotNull PsiFile source);

  @Nullable @DialogMessage
  String verify(PsiFile source);
  @Nullable @DialogMessage
  String verify(PsiDirectory source);
  @Nullable @DialogMessage
  String verify(PsiPackage source);

  /**
   * Searches for conflicts which arise when elements are moved from one module to another
   * 
   * E.g. when target module has no dependency on required library, 
   *      or when usage in the third module has no dependency on the target module 
   *      
   * Impl note: do nothing if elements are kept in the same module
   */
  void analyzeModuleConflicts(@NotNull Collection<? extends PsiElement> elements, 
                              @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                              final UsageInfo[] usages);

  /**
   * @return true if runtime scope of {@code place}'s module contains target destination
   */
  boolean isTargetAccessible(@NotNull Project project, @NotNull VirtualFile place);
}
