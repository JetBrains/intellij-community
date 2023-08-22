// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public final class LanguagePsiElementExternalizer extends LanguageExtension<PsiElementExternalizer> {
  public static final LanguagePsiElementExternalizer INSTANCE = new LanguagePsiElementExternalizer();

  private LanguagePsiElementExternalizer() {
    super("com.intellij.lang.psiElementExternalizer", new PsiElementExternalizer() {
      @Override
      public String getQualifiedName(PsiElement element) {
        return null;
      }

      @Nullable
      @Override
      public PsiElement findByQualifiedName(Project project, @NotNull String qualifiedName) {
        return null;
      }
    });
  }
}
