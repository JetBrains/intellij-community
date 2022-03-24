// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ConvertToStringLiteralAction implements IntentionActionWithFixAllOption {
  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("convert.to.string.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("convert.to.string.family");
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    return PsiUtil.isJavaToken(element, JavaTokenType.CHARACTER_LITERAL);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    if (element != null && PsiUtil.isJavaToken(element, JavaTokenType.CHARACTER_LITERAL)) {
      final String text = StringUtil.unescapeStringCharacters(element.getText());
      final int length = text.length();
      if (length > 1 && text.charAt(0) == '\'' && text.charAt(length - 1) == '\'') {
        final String value = StringUtil.escapeStringCharacters(text.substring(1, length - 1));
        final PsiExpression expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText('"' + value + '"', null);
        final PsiElement literal = expression.getFirstChild();
        if (literal != null && PsiUtil.isJavaToken(literal, JavaTokenType.STRING_LITERAL)) {
          element.replace(literal);
        }
      }
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
