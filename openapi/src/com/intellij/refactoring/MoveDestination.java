/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiPackage;
import com.intellij.util.IncorrectOperationException;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Represents a destination of Move Classes/Packages refactoring.
 * Destination of Move refactoring is generally a single package,
 * and various <code>MoveDestination</code>s control how moved items
 * will be layouted in directories corresponding to target packages.
 *
 * Instances of this interface can be obtained via methods of {@link RefactoringFactory}.
 *
 * @see RefactoringFactory#createSourceFolderPreservingMoveDestination(java.lang.String)
 * @see RefactoringFactory#createSourceRootMoveDestination(java.lang.String, com.intellij.openapi.vfs.VirtualFile)
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

  String verify(PsiFile source);
  String verify(PsiDirectory source);
  String verify(PsiPackage source);

  void analyzeModuleConflicts(final Collection<PsiElement> elements, ArrayList<String> conflicts);
}
