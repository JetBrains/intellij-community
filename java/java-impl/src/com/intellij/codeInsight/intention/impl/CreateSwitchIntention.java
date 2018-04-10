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

import com.intellij.codeInsight.intention.BaseElementAtCaretIntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public class CreateSwitchIntention extends BaseElementAtCaretIntentionAction implements LowPriorityAction {
  public static final String TEXT = "Create switch statement";

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, false);
    String valueToSwitch = expressionStatement.getExpression().getText();
    PsiSwitchStatement switchStatement = (PsiSwitchStatement)new CommentTracker().replaceAndRestoreComments(expressionStatement, "switch (" + valueToSwitch + ") {}");
    CodeStyleManager.getInstance(project).reformat(switchStatement);

    PsiJavaToken lBrace = switchStatement.getBody().getLBrace();
    editor.getCaretModel().moveToOffset(lBrace.getTextOffset() + lBrace.getTextLength());
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, false);
    return expressionStatement != null &&
           expressionStatement.getParent() instanceof PsiCodeBlock &&
           PsiTreeUtil.findChildOfType(expressionStatement.getExpression(), PsiErrorElement.class) == null &&
           isValidTypeForSwitch(expressionStatement.getExpression().getType(), expressionStatement);
  }

  private static boolean isValidTypeForSwitch(@Nullable PsiType type, PsiElement context) {
    if (type == null) {
      return false;
    }

    if (type instanceof PsiClassType) {
      PsiClass resolvedClass = ((PsiClassType)type).resolve();
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
