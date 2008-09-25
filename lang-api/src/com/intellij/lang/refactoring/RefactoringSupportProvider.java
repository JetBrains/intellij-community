/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.lang.refactoring;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Allows a custom language plugin to control the operation of refactorings for
 * files in the language.
 *
 * @author ven
 */

public interface RefactoringSupportProvider {
  /**
   * Checks if the Safe Delete refactoring can be applied to the specified element
   * in the language. The Safe Delete refactoring also requires the plugin to implement
   * Find Usages functionality.
   *
   * @param element the element for which Safe Delete was invoked
   * @return true if Safe Delete is available, false otherwise.
   */
  boolean isSafeDeleteAvailable(PsiElement element);

  /**
   * @return handler for introducing local variables in this language
   * @see com.intellij.refactoring.RefactoringActionHandler
   */
  @Nullable RefactoringActionHandler getIntroduceVariableHandler();

  /**
   * @return handler for extracting methods in this language
   * @see com.intellij.refactoring.RefactoringActionHandler
   */
  @Nullable RefactoringActionHandler getExtractMethodHandler();

  /**
   * @return handler for introducing constants in this language
   * @see com.intellij.refactoring.RefactoringActionHandler
   */
  @Nullable RefactoringActionHandler getIntroduceConstantHandler();

  /**
   * @return handler for introducing fields in this language
   * @see com.intellij.refactoring.RefactoringActionHandler
   */
  @Nullable RefactoringActionHandler getIntroduceFieldHandler();

  boolean doInplaceRenameFor(PsiElement element, PsiElement context);
}
