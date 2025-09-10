// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.spellchecker;

import com.intellij.codeInspection.SuppressManager;
import com.intellij.codeInspection.util.ChronoUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.spellchecker.tokenizer.SpellcheckingStrategy;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;

/**
 * @author shkate@jetbrains.com
 */
public final class JavaSpellcheckingStrategy extends SpellcheckingStrategy implements DumbAware {
  private final MethodNameTokenizerJava myMethodNameTokenizer = new MethodNameTokenizerJava();
  private final DocCommentTokenizer myDocCommentTokenizer = new DocCommentTokenizer();
  private final LiteralExpressionTokenizer myLiteralExpressionTokenizer = new LiteralExpressionTokenizer();
  private final NamedElementTokenizer myNamedElementTokenizer = new NamedElementTokenizer();

  @Override
  public @NotNull Tokenizer getTokenizer(PsiElement element) {
    if (element instanceof PsiMethod) {
      return myMethodNameTokenizer;
    }
    if (element instanceof PsiDocComment) {
      return myDocCommentTokenizer;
    }
    if (element instanceof PsiLiteralExpression literalExpression) {
      if (ChronoUtil.isPatternForDateFormat(literalExpression) || SuppressManager.isSuppressedInspectionName(literalExpression)) {
        return EMPTY_TOKENIZER;
      }
      return myLiteralExpressionTokenizer;
    }
    if (element instanceof PsiNamedElement) {
      return myNamedElementTokenizer;
    }
    if (shouldIgnore(element)) {
      return EMPTY_TOKENIZER;
    }

    return super.getTokenizer(element);
  }

  private boolean shouldIgnore(PsiElement element) {
    return element instanceof PsiComment comment
           && comment.getTokenType() == JavaTokenType.END_OF_LINE_COMMENT
           && useTextLevelSpellchecking();
  }

  @Override
  public boolean useTextLevelSpellchecking() {
    return Registry.is("spellchecker.grazie.enabled");
  }
}
