/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.spellchecker;

import com.intellij.codeInspection.SuppressManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

/**
 * @author shkate@jetbrains.com
 */
public class JavaSpellcheckingStrategy extends SpellcheckingStrategy {
  private final MethodNameTokenizerJava myMethodNameTokenizer = new MethodNameTokenizerJava();
  private final DocCommentTokenizer myDocCommentTokenizer = new DocCommentTokenizer();
  private final LiteralExpressionTokenizer myLiteralExpressionTokenizer = new LiteralExpressionTokenizer();
  private final NamedElementTokenizer myNamedElementTokenizer = new NamedElementTokenizer();

  @NotNull
  @Override
  public Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiMethod) {
      return myMethodNameTokenizer;
    }
    if (element instanceof PsiDocComment) {
      return myDocCommentTokenizer;
    }
    if (element instanceof PsiLiteralExpression) {
      if (SuppressManager.isSuppressedInspectionName((PsiLiteralExpression)element)) {
        return EMPTY_TOKENIZER;
      }
      return myLiteralExpressionTokenizer;
    }
    if (element instanceof PsiNamedElement) {
      return myNamedElementTokenizer;
    }

    return super.getTokenizer(element);
  }
}
