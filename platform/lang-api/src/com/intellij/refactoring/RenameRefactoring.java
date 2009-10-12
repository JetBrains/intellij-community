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

import com.intellij.psi.PsiElement;

import java.util.Collection;
import java.util.Set;

/**
 * @author dsl
 *
 * @see com.intellij.refactoring.RefactoringFactory#createRename(com.intellij.psi.PsiElement, String)
 */
public interface RenameRefactoring extends Refactoring {

  /**
   * Add element to be renamed.
   *
   * @param element element to be renamed.
   * @param newName new name for the element.
   */
  void addElement(PsiElement element, String newName);

  Set<PsiElement> getElements();

  Collection<String> getNewNames();

  void setSearchInComments(boolean value);

  void setSearchInNonJavaFiles(boolean value);

  boolean isSearchInComments();

  boolean isSearchInNonJavaFiles();
}
