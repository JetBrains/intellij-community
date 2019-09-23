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

/*
 * @author max
 */
package com.intellij.lang;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LanguageRefactoringSupport extends LanguageExtension<RefactoringSupportProvider> {
  public static final LanguageRefactoringSupport INSTANCE = new LanguageRefactoringSupport();

  private LanguageRefactoringSupport() {
    super("com.intellij.lang.refactoringSupport", new RefactoringSupportProvider() {});
  }

  @Nullable
  public RefactoringSupportProvider forContext(@NotNull PsiElement element) {
    List<RefactoringSupportProvider> providers = INSTANCE.allForLanguage(element.getLanguage());
    for (RefactoringSupportProvider provider : providers) {
      if (provider.isAvailable(element)) {
        return provider;
      }
    }
    return null;
  }
}