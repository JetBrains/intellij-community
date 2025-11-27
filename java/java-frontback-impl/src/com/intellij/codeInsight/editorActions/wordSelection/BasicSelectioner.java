// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions.wordSelection;

import com.intellij.codeInsight.editorActions.ExtendWordSelectionHandlerBase;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocTag;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public class BasicSelectioner extends ExtendWordSelectionHandlerBase {

  private static Predicate<PsiElement> getElementPredicate() {
    return (e) -> {
      Language language = e.getLanguage();
      return !(language instanceof XMLLanguage || language.isKindOf(XMLLanguage.INSTANCE));
    };
  }

  @Override
  public boolean canSelect(final @NotNull PsiElement e) {
    return
      !(e instanceof PsiWhiteSpace) &&
      !(e instanceof PsiComment) &&
      !(e instanceof PsiCodeBlock) &&
      !(e instanceof PsiArrayInitializerExpression) &&
      !(e instanceof PsiParameterList) &&
      !(e instanceof PsiExpressionList) &&
      !(e instanceof PsiBlockStatement) &&
      !(e instanceof PsiJavaCodeReferenceElement) &&
      !(e instanceof PsiJavaToken &&
        !(e instanceof PsiKeyword)) &&
      !(getElementPredicate().test(e)) &&
      !(e instanceof PsiDocTag);
  }
}
