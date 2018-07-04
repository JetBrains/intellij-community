/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SimplifyBooleanExpressionAction implements IntentionAction{
  private String myText = getFamilyName();

  @Override
  @NotNull
  public String getText() {
    return myText;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return SimplifyBooleanExpressionFix.FAMILY_NAME;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiExpression expression = getExpressionToSimplify(editor, file);
    if (expression != null && SimplifyBooleanExpressionFix.canBeSimplified(expression)) {
      Object o = JavaConstantExpressionEvaluator.computeConstantExpression(expression, false);
      myText = o instanceof Boolean ? SimplifyBooleanExpressionFix.getIntentionText(expression, (Boolean)o) : getFamilyName();
      return true;
    }
    return false;
  }

  @Nullable
  private static PsiExpression getExpressionToSimplify(@NotNull final Editor editor, @NotNull final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
    PsiElement parent = expression;
    while (parent instanceof PsiExpression && (PsiType.BOOLEAN.equals(((PsiExpression)parent).getType()) || parent instanceof PsiConditionalExpression)) {
      expression = (PsiExpression)parent;
      parent = parent.getParent();
    }
    return expression;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiExpression expression = getExpressionToSimplify(editor, file);
    if (expression != null) {
      SimplifyBooleanExpressionFix.simplifyExpression(expression);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}