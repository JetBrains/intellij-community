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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.text.LiteralFormatUtil;
import org.jetbrains.annotations.NotNull;

public class InsertLiteralUnderscoresAction extends PsiElementBaseIntentionAction {
  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    if (!PsiUtil.isLanguageLevel7OrHigher(element)) return false;

    final PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(element, PsiLiteralExpression.class, false);
    if (literalExpression == null) return false;

    final PsiType type = literalExpression.getType();
    if (!PsiType.INT.equals(type) && !PsiType.LONG.equals(type) &&
        !PsiType.FLOAT.equals(type) && !PsiType.DOUBLE.equals(type)) return false;

    final String text = literalExpression.getText();
    if (text == null || text.contains("_")) return false;

    final String converted = LiteralFormatUtil.format(text, type);
    return converted.length() != text.length();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    final PsiLiteralExpression literalExpression = PsiTreeUtil.getParentOfType(element, PsiLiteralExpression.class, false);
    if (literalExpression == null) return;

    final String text = literalExpression.getText();
    final PsiType type = literalExpression.getType();
    final String converted = LiteralFormatUtil.format(text, type);
    if (converted.length() == text.length()) return;

    final PsiExpression replacement = JavaPsiFacade.getElementFactory(project).createExpressionFromText(converted, null);
    literalExpression.replace(replacement);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.underscores.in.literals.family");
  }

  @NotNull
  @Override
  public String getText() {
    return JavaBundle.message("intention.insert.literal.underscores");
  }
}
