/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
public class InlineElseBranchAction extends PsiElementBaseIntentionAction {
  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiIfStatement) {
      PsiIfStatement ifStatement = (PsiIfStatement)parent;
      PsiStatement elseBranch = ifStatement.getElseBranch();
      PsiKeyword elseKeyword = ifStatement.getElseElement();
      if (elseBranch != null && elseKeyword != null) {
        InvertIfConditionAction.addAfter(ifStatement, elseBranch);
        elseBranch.delete();
        elseKeyword.delete();
      }
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (element instanceof PsiKeyword && ((PsiKeyword)element).getTokenType() == JavaTokenType.ELSE_KEYWORD) {
      PsiElement parent = element.getParent();
      return parent instanceof PsiIfStatement &&
             ((PsiIfStatement)parent).getElseBranch() != null &&
             parent.getParent() instanceof PsiCodeBlock;
    }
    return false;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.inline.else.branch");
  }
}
