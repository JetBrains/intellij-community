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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class CreateSwitchIntention extends BaseElementAtCaretIntentionAction {
  public static final String TEXT = "Create switch statement";

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(element)) {
      return;
    }
    final PsiExpressionStatement expressionStatement = resolveExpressionStatement(element);
    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiSwitchStatement switchStatement = (PsiSwitchStatement)elementFactory
      .createStatementFromText(String.format("switch (%s) {}", expressionStatement.getExpression().getText()), null);
    switchStatement = (PsiSwitchStatement)expressionStatement.replace(switchStatement);
    CodeStyleManager.getInstance(project).reformat(switchStatement);

    final PsiJavaToken lBrace = switchStatement.getBody().getLBrace();
    editor.getCaretModel().moveToOffset(lBrace.getTextOffset() + lBrace.getTextLength());
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, @NotNull final PsiElement element) {
    final PsiExpressionStatement expressionStatement = resolveExpressionStatement(element);
    return expressionStatement != null && isValidTypeForSwitch(expressionStatement.getExpression().getType(), expressionStatement);
  }

  private static PsiExpressionStatement resolveExpressionStatement(final PsiElement element) {
    if (element instanceof PsiExpressionStatement) {
      return (PsiExpressionStatement)element;
    } else {
      final PsiStatement psiStatement = PsiTreeUtil.getParentOfType(element, PsiStatement.class);
      return psiStatement instanceof PsiExpressionStatement ? (PsiExpressionStatement)psiStatement : null;
    }
  }

  private static boolean isValidTypeForSwitch(@Nullable final PsiType type, final PsiElement context) {
    if (type == null) {
      return false;
    }

    if (type instanceof PsiClassType) {
      final PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass == null) {
        return false;
      }
      return (PsiUtil.isLanguageLevel5OrHigher(context) && resolvedClass.isEnum())
             || (PsiUtil.isLanguageLevel7OrHigher(context) && CommonClassNames.JAVA_LANG_STRING.equals(resolvedClass.getQualifiedName()));
    }
    return type.equals(PsiType.INT) || type.equals(PsiType.BYTE) || type.equals(PsiType.SHORT) || type.equals(PsiType.CHAR);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return TEXT;
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
