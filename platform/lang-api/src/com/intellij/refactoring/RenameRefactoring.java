// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Set;

/**
 * @see RefactoringFactory#createRename(PsiElement, String)
 */
public interface RenameRefactoring extends Refactoring {

  /**
   * Add element to be renamed.
   *
   * @param element element to be renamed.
   * @param newName new name for the element.
   */
  void addElement(PsiElement element, String newName);

  @Unmodifiable Set<PsiElement> getElements();

  @Unmodifiable
  Collection<String> getNewNames();

  void setSearchInComments(boolean value);

  void setSearchInNonJavaFiles(boolean value);

  boolean isSearchInComments();

  boolean isSearchInNonJavaFiles();

  @ApiStatus.Experimental
  void respectEnabledAutomaticRenames();
  @ApiStatus.Experimental
  void respectAllAutomaticRenames();

  @ApiStatus.Experimental
  boolean hasNonCodeUsages();
}
