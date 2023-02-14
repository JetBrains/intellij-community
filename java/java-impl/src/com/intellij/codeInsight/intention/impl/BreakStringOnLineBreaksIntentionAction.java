// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Objects;

public class BreakStringOnLineBreaksIntentionAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!(element instanceof PsiJavaToken token)) {
      return false;
    }

    if (token.getTokenType() != JavaTokenType.STRING_LITERAL) {
      return false;
    }

    final String text = token.getText();
    if (text == null) {
      return false;
    }

    final int indexOfSlashN = text.indexOf("\\n");
    if (indexOfSlashN == -1 || Objects.equals(text.substring(indexOfSlashN), "\\n\"")){
      return false;
    }

    final int indexOfSlashNSlashR = text.indexOf("\\n\\r");
    if (indexOfSlashNSlashR != -1 && Objects.equals(text.substring(indexOfSlashNSlashR), "\\n\\r\"")){
      return false;
    }

    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiJavaToken token)) {
      return;
    }

    if (token.getTokenType() != JavaTokenType.STRING_LITERAL) {
      return;
    }


    final String text = token.getText();
    if (text == null) {
      return;
    }

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    token.getParent().replace(factory.createExpressionFromText(breakOnLineBreaks(text), element));
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
  public String getText() {
    return JavaBundle.message("intention.break.string.on.line.breaks.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }
}
