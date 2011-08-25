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
package com.intellij.psi;

import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.psi.templateLanguages.OuterLanguageElement;

/**
 * Visitor which can be used to visit elements for all languages.
 *
 * @see PsiRecursiveElementVisitor
 */
public abstract class PsiElementVisitor {
  public void visitElement(PsiElement element) {
    ProgressIndicatorProvider.checkCanceled();
  }

  public void visitFile(PsiFile file) {
    visitElement(file);
  }

  public void visitBinaryFile(PsiBinaryFile file){
    visitFile(file);
  }

  public void visitPlainTextFile(PsiPlainTextFile file){
    visitFile(file);
  }

  public void visitErrorElement(PsiErrorElement element) {
    visitElement(element);
  }

  public void visitPlainText(PsiPlainText content) {
    visitElement(content);
  }

  public void visitDirectory(PsiDirectory dir) {
    visitElement(dir);
  }

  public void visitComment(PsiComment comment) {
    visitElement(comment);
  }

  public void visitWhiteSpace(PsiWhiteSpace space) {
    visitElement(space);
  }

  public void visitOuterLanguageElement(OuterLanguageElement element) {
    visitElement(element);
  }
}
