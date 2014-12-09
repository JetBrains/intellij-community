/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Batkovich
 */
public class SwapIfStatementsIntentionAction extends PsiElementBaseIntentionAction {
  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    final PsiIfStatement ifStatement = (PsiIfStatement)element.getParent();
    final PsiIfStatement nestedIfStatement = (PsiIfStatement) ifStatement.getElseBranch();
    assert nestedIfStatement != null;

    final PsiExpression condition = ifStatement.getCondition();
    final PsiExpression nestedCondition = nestedIfStatement.getCondition();

    final PsiStatement thenBranch = ifStatement.getThenBranch();
    final PsiStatement nestedThenBranch = nestedIfStatement.getThenBranch();

    assert condition != null;
    assert nestedCondition != null;
    assert thenBranch != null;
    assert nestedThenBranch != null;

    final PsiElement conditionCopy = condition.copy();
    condition.replace(nestedCondition);
    nestedCondition.replace(conditionCopy);

    final PsiElement thenBranchCopy = thenBranch.copy();
    thenBranch.replace(nestedThenBranch);
    nestedThenBranch.replace(thenBranchCopy);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    if (!(element instanceof PsiKeyword) || !PsiKeyword.ELSE.equals(element.getText())) {
      return false;
    }
    final PsiElement parent = element.getParent();
    return parent instanceof PsiIfStatement && ((PsiIfStatement)parent).getElseBranch() instanceof PsiIfStatement;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Swap 'if' statements";
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
