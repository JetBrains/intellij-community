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

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ven
 */
public class ConcatenationToMessageFormatAction implements IntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.replace.concatenation.with.formatted.output.family");
  }

  @Override
  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.replace.concatenation.with.formatted.output.text");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiElement element = findElementAtCaret(editor, file);
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(element);
    if (concatenation == null) return;
    StringBuilder formatString = new StringBuilder();
    List<PsiExpression> args = new ArrayList<>();
    PsiConcatenationUtil.buildFormatString(concatenation, formatString, args, false);

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression call = (PsiMethodCallExpression)
      factory.createExpressionFromText("java.text.MessageFormat.format()", concatenation);
    PsiExpressionList argumentList = call.getArgumentList();
    PsiExpression formatArgument = factory.createExpressionFromText("\"" + formatString.toString() + "\"", null);
    argumentList.add(formatArgument);
    if (PsiUtil.isLanguageLevel5OrHigher(file)) {
      for (PsiExpression arg : args) {
        argumentList.add(arg);
      }
    }
    else {
      final PsiNewExpression arrayArg = (PsiNewExpression)factory.createExpressionFromText("new java.lang.Object[]{}", null);
      final PsiArrayInitializerExpression arrayInitializer = arrayArg.getArrayInitializer();
      assert arrayInitializer != null;
      for (PsiExpression arg : args) {
        arrayInitializer.add(arg);
      }
      argumentList.add(arrayArg);
    }
    call = (PsiMethodCallExpression) JavaCodeStyleManager.getInstance(project).shortenClassReferences(call);
    call = (PsiMethodCallExpression) CodeStyleManager.getInstance(project).reformat(call);
    new CommentTracker().replaceAndRestoreComments(concatenation, call);
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (PsiUtil.getLanguageLevel(file).compareTo(LanguageLevel.JDK_1_4) < 0) return false;
    final PsiElement element = findElementAtCaret(editor, file);
    final PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(element);
    return concatenation != null && !AnnotationUtil.isInsideAnnotation(concatenation) && !PsiUtil.isConstantExpression(concatenation);
  }

  @Nullable
  private static PsiElement findElementAtCaret(Editor editor, PsiFile file) {
    return file.findElementAt(editor.getCaretModel().getOffset());
  }

  @Nullable
  private static PsiPolyadicExpression getEnclosingLiteralConcatenation(final PsiElement element) {
    PsiPolyadicExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class, false, PsiMember.class);
    if (binaryExpression == null) return null;
    final PsiClassType stringType = PsiType.getJavaLangString(element.getManager(), element.getResolveScope());
    if (!stringType.equals(binaryExpression.getType())) return null;
    while (true) {
      final PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiPolyadicExpression)) return binaryExpression;
      PsiPolyadicExpression parentBinaryExpression = (PsiPolyadicExpression)parent;
      if (!stringType.equals(parentBinaryExpression.getType())) return binaryExpression;
      binaryExpression = parentBinaryExpression;
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
