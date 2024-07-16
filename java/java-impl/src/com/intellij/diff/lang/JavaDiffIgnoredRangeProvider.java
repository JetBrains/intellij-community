// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.lang;

import com.intellij.java.JavaBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class JavaDiffIgnoredRangeProvider extends LangDiffIgnoredRangeProvider {
  @Override
  public @NotNull String getDescription() {
    return JavaBundle.message("ignore.imports.and.formatting");
  }

  @Override
  protected boolean accepts(@NotNull Project project, @NotNull Language language) {
    return JavaLanguage.INSTANCE.equals(language);
  }

  @Override
  protected @NotNull List<TextRange> computeIgnoredRanges(@NotNull Project project, @NotNull CharSequence text, @NotNull Language language) {
    return ReadAction.compute(() -> {
      List<TextRange> result = new ArrayList<>();
      PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("", language, text);

      psiFile.accept(new PsiElementVisitor() {
        @Override
        public void visitElement(@NotNull PsiElement element) {
          if (element.getTextLength() == 0) return;

          if (isIgnored(element)) {
            result.add(element.getTextRange());
          }
          else {
            element.acceptChildren(this);
          }
        }
      });
      return result;
    });
  }

  private static boolean isIgnored(@NotNull PsiElement element) {
    if (element instanceof PsiWhiteSpace) return true;
    if (element instanceof PsiImportList) return true;
    return false;
  }
}
