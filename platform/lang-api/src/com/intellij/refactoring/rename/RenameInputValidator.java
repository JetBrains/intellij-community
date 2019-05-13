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

package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author Gregory.Shrago
 */
public interface RenameInputValidator {
  ExtensionPointName<RenameInputValidator> EP_NAME = ExtensionPointName.create("com.intellij.renameInputValidator");

  @NotNull
  ElementPattern<? extends PsiElement> getPattern();

  /**
   * Is invoked for elements accepted by pattern {@link #getPattern()}.
   * Should return true if {@link RenameInputValidatorEx} is intended to return custom error message,
   * otherwise default message "newName is not a valid identifier" would be shown
   */
  boolean isInputValid(@NotNull final String newName, @NotNull final PsiElement element, @NotNull final ProcessingContext context);
}
