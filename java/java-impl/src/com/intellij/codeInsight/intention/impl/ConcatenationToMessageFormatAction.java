// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.psi.util.PsiLiteralUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class ConcatenationToMessageFormatAction extends PsiUpdateModCommandAction<PsiElement> {
  public ConcatenationToMessageFormatAction() {
    super(PsiElement.class);
  }
  
  @Override
  @NotNull
  public String getFamilyName() {
    return JavaBundle.message("intention.replace.concatenation.with.formatted.output.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(element);
    if (concatenation == null) return;
    List<PsiExpression> args = new ArrayList<>();
    final String formatString =
      StringUtil.escapeStringCharacters(PsiConcatenationUtil.buildUnescapedFormatString(concatenation, false, args));

    Project project = context.project();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    PsiMethodCallExpression call = (PsiMethodCallExpression)
      factory.createExpressionFromText("java.text.MessageFormat.format()", concatenation);
    PsiExpressionList argumentList = call.getArgumentList();
    boolean textBlocks = ContainerUtil.exists(concatenation.getOperands(),
                                              operand -> operand instanceof PsiLiteralExpression literal && literal.isTextBlock());
    final String expressionText;
    if (textBlocks) {
      expressionText = Arrays.stream(formatString.split("\n"))
        .map(s -> PsiLiteralUtil.escapeTextBlockCharacters(s))
        .collect(Collectors.joining("\n", "\"\"\"\n", "\"\"\""));
    }
    else {
      expressionText = "\"" + formatString + "\"";
    }
    PsiExpression formatArgument = factory.createExpressionFromText(expressionText, null);
    argumentList.add(formatArgument);
    if (PsiUtil.isLanguageLevel5OrHigher(context.file())) {
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
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (PsiUtil.getLanguageLevel(context.file()).compareTo(LanguageLevel.JDK_1_4) < 0) return null;
    final PsiPolyadicExpression concatenation = getEnclosingLiteralConcatenation(element);
    if (concatenation == null || AnnotationUtil.isInsideAnnotation(concatenation) || PsiUtil.isConstantExpression(concatenation)) {
      return null;
    }
    return Presentation.of(JavaBundle.message("intention.replace.concatenation.with.formatted.output.text"));
  }

  @Nullable
  private static PsiPolyadicExpression getEnclosingLiteralConcatenation(final PsiElement element) {
    PsiPolyadicExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PsiPolyadicExpression.class, false, PsiMember.class);
    if (binaryExpression == null) return null;
    final PsiClassType stringType = PsiType.getJavaLangString(element.getManager(), element.getResolveScope());
    if (!stringType.equals(binaryExpression.getType())) return null;
    while (true) {
      final PsiElement parent = binaryExpression.getParent();
      if (!(parent instanceof PsiPolyadicExpression parentBinaryExpression)) return binaryExpression;
      if (!stringType.equals(parentBinaryExpression.getType())) return binaryExpression;
      binaryExpression = parentBinaryExpression;
    }
  }
}
