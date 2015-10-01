/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.rename;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 * Date: 3/14/11
 */
public interface RenameInputValidatorEx extends RenameInputValidator {
  /**
   * Called only if all input validators ({@link RenameInputValidator}) accept 
   * the new name in {@link #isInputValid(String, PsiElement, ProcessingContext)} 
   * and name is a valid identifier for a language of the element
   * 
   * @return null if newName is a valid name, custom error message otherwise
   */
  @Nullable
  String getErrorMessage(String newName, Project project);
}
