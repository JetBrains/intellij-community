/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring;

import com.intellij.psi.PsiElement;

import java.util.List;

/**
 * @author dsl
 */
public interface RenameRefactoring extends Refactoring {
  void addElement(PsiElement element, String newName);

  List<PsiElement> getElements();
  List<String> getNewNames();

  void setShouldRenameVariables(boolean value);

  void setShouldRenameInheritors(boolean value);

  void setSearchInComments(boolean value);

  void setSearchInNonJavaFiles(boolean value);

  boolean isSearchInComments();

  boolean isSearchInNonJavaFiles();
}
