/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.templateLanguages.OuterLanguageElement;
import org.jetbrains.annotations.NotNull;

/**
 * Visitor which can be used to visit elements for all languages.
 *
 * @see PsiRecursiveElementVisitor
 */
public abstract class PsiElementVisitor {
  public static final PsiElementVisitor EMPTY_VISITOR = new PsiElementVisitor() { };

  public void visitBinaryFile(@NotNull PsiBinaryFile file){
    visitFile(file);
  }

  public void visitComment(@NotNull PsiComment comment) {
    visitElement(comment);
  }

  public void visitDirectory(@NotNull PsiDirectory dir) {
    visitElement(dir);
  }

  public void visitElement(@NotNull PsiElement element) {
    ProgressIndicatorProvider.checkCanceled();
  }

  public void visitErrorElement(@NotNull PsiErrorElement element) {
    visitElement(element);
  }

  public void visitFile(@NotNull PsiFile psiFile) {
    visitElement(psiFile);
  }

  public void visitOuterLanguageElement(@NotNull OuterLanguageElement element) {
    visitElement(element);
  }

  public void visitPlainText(@NotNull PsiPlainText content) {
    visitElement(content);
  }

  public void visitPlainTextFile(@NotNull PsiPlainTextFile file){
    visitFile(file);
  }

  public void visitWhiteSpace(@NotNull PsiWhiteSpace space) {
    visitElement(space);
  }
}
