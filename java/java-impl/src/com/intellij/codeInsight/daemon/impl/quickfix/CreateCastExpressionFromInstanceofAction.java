/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class CreateCastExpressionFromInstanceofAction extends CreateLocalVarFromInstanceofAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    boolean available = super.isAvailable(project, editor, file);
    if (!available) return false;
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    if (instanceOfExpression == null) return false;
    PsiTypeElement checkType = instanceOfExpression.getCheckType();
    if (checkType == null) return false;
    PsiType type = checkType.getType();
    String castTo = type.getPresentableText();
    setText(CodeInsightBundle.message("cast.to.0", castTo));
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) {
    PsiInstanceOfExpression instanceOfExpression = getInstanceOfExpressionAtCaret(editor, file);
    assert instanceOfExpression.getContainingFile() == file : instanceOfExpression.getContainingFile() + "; file="+file;
    PsiElement decl = createAndInsertCast(instanceOfExpression, editor, file);
    if (decl == null) return;
    decl = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(decl);
    if (decl == null) return;
    decl = CodeStyleManager.getInstance(project).reformat(decl);
    editor.getCaretModel().moveToOffset(decl.getTextRange().getEndOffset());
  }

  @Nullable
  private static PsiElement createAndInsertCast(final PsiInstanceOfExpression instanceOfExpression, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiElementFactory factory = JavaPsiFacade.getInstance(instanceOfExpression.getProject()).getElementFactory();
    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText("((a)b)", instanceOfExpression);

    PsiParenthesizedExpression paren = (PsiParenthesizedExpression)statement.getExpression();
    PsiTypeCastExpression cast = (PsiTypeCastExpression)paren.getExpression();
    PsiType castType = instanceOfExpression.getCheckType().getType();
    cast.getCastType().replace(factory.createTypeElement(castType));
    cast.getOperand().replace(instanceOfExpression.getOperand());

    final PsiStatement statementInside = isNegated(instanceOfExpression) ? null : getExpressionStatementInside(file, editor, instanceOfExpression.getOperand());
    if (statementInside != null) {
      return statementInside.replace(statement);
    }
    else {
      return insertAtAnchor(instanceOfExpression, statement);
    }
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("cast.expression");
  }
}