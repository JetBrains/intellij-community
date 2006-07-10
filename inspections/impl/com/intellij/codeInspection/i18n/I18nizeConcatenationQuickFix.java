/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection.i18n;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.ide.fileTemplates.FileTemplateManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class I18nizeConcatenationQuickFix extends I18nizeQuickFix{

  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.i18n.I18nizeConcatenationQuickFix");
  @NonNls private static final String PARAMETERS_OPTION_KEY = "PARAMETERS";

  public void checkApplicability(final PsiFile psiFile, final Editor editor) throws IncorrectOperationException {
  }

  private static PsiLiteralExpression getContainingLiteral(final PsiBinaryExpression concatenation) {
    PsiExpression operand = concatenation.getLOperand();
    PsiLiteralExpression literalExpression = null;
    if (operand instanceof PsiLiteralExpression) {
      literalExpression = (PsiLiteralExpression)operand;
    }
    else {
      operand = concatenation.getROperand();
      if (operand instanceof PsiLiteralExpression) {
        literalExpression = (PsiLiteralExpression)operand;
      }
    }
    return literalExpression;
  }

  public I18nizeQuickFixDialog createDialog(PsiFile psiFile, Editor editor, Project project) {
    PsiBinaryExpression concatenation = ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(psiFile,editor);
    PsiLiteralExpression literalExpression = getContainingLiteral(concatenation);
    return createDialog(project, psiFile, literalExpression);
  }

  public String getName() {
    return CodeInsightBundle.message("quickfix.i18n.concatentation");
  }

  protected PsiElement doReplacementInJava(@NotNull final PsiFile psiFile,
                                           final Editor editor,
                                           @Nullable PsiLiteralExpression literalExpression,
                                           String i18nizedText) throws IncorrectOperationException {
    PsiBinaryExpression concatenation = ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(psiFile,editor);

    PsiExpression expression = psiFile.getManager().getElementFactory().createExpressionFromText(i18nizedText, concatenation);
    return concatenation.replace(expression);
  }

  private static String composeParametersText(final List<PsiExpression> args) {
    final StringBuffer result = new StringBuffer();
    for (Iterator<PsiExpression> iterator = args.iterator(); iterator.hasNext();) {
      PsiExpression psiExpression = iterator.next();
      result.append(psiExpression.getText());
      if (iterator.hasNext()) {
        result.append(",");
      }
    }
    return result.toString();
  }

  protected I18nizeQuickFixDialog createDialog(final Project project, final PsiFile context, final PsiLiteralExpression literalExpression) {
    PsiBinaryExpression concatenation = ConcatenationToMessageFormatAction.getEnclosingLiteralConcatenation(literalExpression);
    StringBuffer formatString = new StringBuffer();
    final List<PsiExpression> args = new ArrayList<PsiExpression>();
    try {
      ArrayList<PsiExpression> argsToCombine = new ArrayList<PsiExpression>();
      ConcatenationToMessageFormatAction.calculateFormatAndArguments(concatenation, formatString, args, argsToCombine, false);
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }

    String value = ConcatenationToMessageFormatAction.prepareString(formatString.toString());

    return new I18nizeQuickFixDialog(project, context, literalExpression, value, true, true) {
      protected String getTemplateName() {
        return FileTemplateManager.TEMPLATE_I18NIZED_CONCATENATION;
      }

      protected void addAdditionalAttributes(final Map<String, String> attributes) {
        attributes.put(PARAMETERS_OPTION_KEY, composeParametersText(args));
      }
    };
  }
}
