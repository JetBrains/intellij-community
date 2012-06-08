/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class IntroduceVariableAction extends BaseRunRefactoringAction<IntroduceVariableHandler> {

  @NotNull
  @Override
  public String getText() {
    return CodeInsightBundle.message("intention.introduce.variable.text");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiElement element = getElement(editor, file);
    if (element == null) {
      return false;
    }
    final PsiStatement statement = PsiTreeUtil.getParentOfType(element, PsiStatement.class, false);

    if (statement == null || !(statement instanceof PsiExpressionStatement)) {
      return false;
    }

    final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)statement;
    final PsiExpression expression = expressionStatement.getExpression();

    return expression.getType() != PsiType.VOID && !(expression instanceof PsiAssignmentExpression);
  }

  @Nullable
  protected static PsiElement getElement(Editor editor, @NotNull PsiFile file) {
    if (!file.getManager().isInProject(file)) return null;
    final CaretModel caretModel = editor.getCaretModel();
    final int position = caretModel.getOffset();
    return file.findElementAt(position);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    new IntroduceVariableHandler().invoke(project, editor, file, null);
  }
}
