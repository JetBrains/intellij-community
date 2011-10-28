/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ConvertToStringLiteralAction implements IntentionAction {
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
      final String text = element.getText();
      final int length = text.length();
      if (length > 1 && text.charAt(0) == '\'' && text.charAt(length - 1) == '\'') {
        final StringBuilder sb = new StringBuilder(text);
        sb.replace(0, 1, "\"");
        sb.replace(length - 1, length, "\"");
        final PsiExpression expression = JavaPsiFacade.getElementFactory(project).createExpressionFromText(sb.toString(), null);
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
