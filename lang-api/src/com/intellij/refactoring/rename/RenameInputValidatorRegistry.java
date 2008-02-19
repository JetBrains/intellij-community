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

/**
 * @author Dmitry Avdeev
 */
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.patterns.ElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class RenameInputValidatorRegistry {
  private static RenameInputValidatorRegistry ourInstance = new RenameInputValidatorRegistry();

  public static RenameInputValidatorRegistry getInstance() {
    return ourInstance;
  }

  private List<Pair<ElementPattern<? extends PsiElement>,RenameInputValidator>> myValidators = new ArrayList<Pair<ElementPattern<? extends PsiElement>, RenameInputValidator>>();

  private RenameInputValidatorRegistry() {
  }

  public void registerInputValidator(@NotNull final ElementPattern<? extends PsiElement> pattern, @NotNull final RenameInputValidator validator) {
    myValidators.add(Pair.<ElementPattern<? extends PsiElement>, RenameInputValidator>create(pattern, validator));
  }

  @Nullable
  public Condition<String> getInputValidator(final PsiElement element) {
    for (final Pair<ElementPattern<? extends PsiElement>, RenameInputValidator> pair: myValidators) {
      final ProcessingContext context = new ProcessingContext();
      if (pair.first.accepts(element, context)) {
        return new Condition<String>() {
          public boolean value(final String s) {
            return pair.getSecond().isInputValid(s, element, context);
          }
        };
      }
    }
    return null;
  }
}
