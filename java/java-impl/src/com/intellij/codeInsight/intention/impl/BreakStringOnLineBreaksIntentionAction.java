// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiJavaToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public final class BreakStringOnLineBreaksIntentionAction extends PsiUpdateModCommandAction<PsiJavaToken> {
  public BreakStringOnLineBreaksIntentionAction() {
    super(PsiJavaToken.class);
  }
  
  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiJavaToken token) {
    if (token.getTokenType() != JavaTokenType.STRING_LITERAL) return null;

    final String text = token.getText();
    if (text == null) return null;

    final int indexOfSlashN = text.indexOf("\\n");
    if (indexOfSlashN == -1 || Objects.equals(text.substring(indexOfSlashN), "\\n\""))return null;

    final int indexOfSlashNSlashR = text.indexOf("\\n\\r");
    if (indexOfSlashNSlashR != -1 && Objects.equals(text.substring(indexOfSlashNSlashR), "\\n\\r\""))return null;

    return Presentation.of(getFamilyName());
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiJavaToken token, @NotNull ModPsiUpdater updater) {
    if (token.getTokenType() != JavaTokenType.STRING_LITERAL) return;

    final String text = token.getText();
    if (text == null) return;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(context.project());
    token.getParent().replace(factory.createExpressionFromText(breakOnLineBreaks(text), token));
  }


  @NotNull
  private static String breakOnLineBreaks(@NotNull String string) {
    final String result = StringUtil.replace(
      string,
      Arrays.asList("\\n\\r", "\\n"),
      Arrays.asList("\\n\\r\" + \n\"", "\\n\" + \n\"")
    );

    final String redundantSuffix = " + \n\"\"";

    return result.endsWith(redundantSuffix) ? result.substring(0, result.length() - redundantSuffix.length()) : result;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.break.string.on.line.breaks.text");
  }
}
