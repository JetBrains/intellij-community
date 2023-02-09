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
import com.intellij.java.JavaBundle;
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

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, false);
    String valueToSwitch = expressionStatement.getExpression().getText();
    PsiSwitchStatement switchStatement = (PsiSwitchStatement)new CommentTracker().replaceAndRestoreComments(expressionStatement, "switch (" + valueToSwitch + ") {}");
    CodeStyleManager.getInstance(project).reformat(switchStatement);

    PsiCodeBlock body = switchStatement.getBody();
    PsiJavaToken lBrace = body == null ? null : body.getLBrace();
    if (lBrace != null) {
      editor.getCaretModel().moveToOffset(lBrace.getTextRange().getEndOffset());
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    PsiExpressionStatement expressionStatement = PsiTreeUtil.getParentOfType(element, PsiExpressionStatement.class, false);
    return expressionStatement != null &&
           expressionStatement.getParent() instanceof PsiCodeBlock &&
           !(expressionStatement.getExpression() instanceof PsiAssignmentExpression) &&
           !(expressionStatement.getExpression() instanceof PsiLiteralExpression) &&
           PsiTreeUtil.findChildOfType(expressionStatement.getExpression(), PsiErrorElement.class) == null &&
           isValidTypeForSwitch(expressionStatement.getExpression().getType(), expressionStatement);
  }

  private static boolean isValidTypeForSwitch(@Nullable PsiType type, PsiElement context) {
    if (type instanceof PsiClassType) {
      PsiClass resolvedClass = ((PsiClassType)type).resolve();
      if (resolvedClass == null) {
        return false;
      }
      return (PsiUtil.isLanguageLevel5OrHigher(context) &&
              (resolvedClass.isEnum() || isSuitablePrimitiveType(PsiPrimitiveType.getUnboxedType(type))))
             || (PsiUtil.isLanguageLevel7OrHigher(context) && CommonClassNames.JAVA_LANG_STRING.equals(resolvedClass.getQualifiedName()));
    }
    return isSuitablePrimitiveType(type);
  }

  private static boolean isSuitablePrimitiveType(@Nullable PsiType type) {
    if (type == null) {
      return false;
    }
    return type.equals(PsiTypes.intType()) || type.equals(PsiTypes.byteType()) || type.equals(PsiTypes.shortType()) || type.equals(PsiTypes.charType());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("intention.create.switch.statement");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }
}
