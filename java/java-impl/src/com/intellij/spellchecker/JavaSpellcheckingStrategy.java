// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.util.ChronoUtil;
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
    if (element instanceof PsiLiteralExpression literalExpression) {
      if (SuppressManager.isSuppressedInspectionName(literalExpression) ||
          ChronoUtil.isPatternForDateFormat(literalExpression)) {
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
