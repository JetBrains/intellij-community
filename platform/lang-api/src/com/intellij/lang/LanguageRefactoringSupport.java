// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class LanguageRefactoringSupport extends LanguageExtension<RefactoringSupportProvider> {
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