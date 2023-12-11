/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
  @NotNull
  @Override
  public String getDescription() {
    return JavaBundle.message("ignore.imports.and.formatting");
  }

  @Override
  protected boolean accepts(@NotNull Project project, @NotNull Language language) {
    return JavaLanguage.INSTANCE.equals(language);
  }

  @NotNull
  @Override
  protected List<TextRange> computeIgnoredRanges(@NotNull Project project, @NotNull CharSequence text, @NotNull Language language) {
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
