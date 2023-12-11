// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ConvertToStringLiteralAction extends PsiUpdateModCommandAction<PsiJavaToken> {
  
  public ConvertToStringLiteralAction() {
    super(PsiJavaToken.class);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaToken element) {
    if (element.getTokenType() != JavaTokenType.CHARACTER_LITERAL) return null;
    return Presentation.of(QuickFixBundle.message("convert.to.string.text")).withFixAllOption(this);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("convert.to.string.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiJavaToken element, @NotNull ModPsiUpdater updater) {
    final String text = StringUtil.unescapeStringCharacters(element.getText());
    final int length = text.length();
    if (length > 1 && text.charAt(0) == '\'' && text.charAt(length - 1) == '\'') {
      final String value = StringUtil.escapeStringCharacters(text.substring(1, length - 1));
      final PsiExpression expression = JavaPsiFacade.getElementFactory(context.project()).createExpressionFromText('"' + value + '"', null);
      final PsiElement literal = expression.getFirstChild();
      if (literal != null && PsiUtil.isJavaToken(literal, JavaTokenType.STRING_LITERAL)) {
        element.replace(literal);
      }
    }
  }
}
