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

import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.util.Function;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.hash.LinkedHashMap;
import org.jetbrains.annotations.Nullable;

public class RenameInputValidatorRegistry {
  private RenameInputValidatorRegistry() {
  }

  @Nullable
  public static Condition<String> getInputValidator(final PsiElement element) {
    final LinkedHashMap<RenameInputValidator, ProcessingContext> acceptedValidators = new LinkedHashMap<>();
    for(final RenameInputValidator validator: Extensions.getExtensions(RenameInputValidator.EP_NAME)) {
      final ProcessingContext context = new ProcessingContext();
      if (validator.getPattern().accepts(element, context)) {
        acceptedValidators.put(validator, context);
      }
    }
    return acceptedValidators.isEmpty() ? null : (Condition<String>)s -> {
      for (RenameInputValidator validator : acceptedValidators.keySet()) {
        if (!validator.isInputValid(s, element, acceptedValidators.get(validator))) {
          return false;
        }
      }
      return true;
    };
  }

  @Nullable
  public static Function<String, String> getInputErrorValidator(final PsiElement element) {
    final LinkedHashMap<RenameInputValidatorEx, ProcessingContext> acceptedValidators = new LinkedHashMap<>();
    for(final RenameInputValidator validator: Extensions.getExtensions(RenameInputValidator.EP_NAME)) {
      final ProcessingContext context = new ProcessingContext();
      if (validator instanceof RenameInputValidatorEx && validator.getPattern().accepts(element, context)) {
        acceptedValidators.put((RenameInputValidatorEx)validator, context);
      }
    }

    return acceptedValidators.isEmpty() ? null : (Function<String, String>)newName -> {
      for (RenameInputValidatorEx validator : acceptedValidators.keySet()) {
        final String message = validator.getErrorMessage(newName, element.getProject());
        if (message != null) {
          return message;
        }
      }
      return null;
    };
  }
}
