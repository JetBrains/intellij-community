// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Dmitry Batkovich
 */
public final class CreateSwitchIntention extends PsiUpdateModCommandAction<PsiExpressionStatement> {
  public CreateSwitchIntention() {
    super(PsiExpressionStatement.class);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiExpressionStatement expressionStatement, @NotNull ModPsiUpdater updater) {
    String valueToSwitch = expressionStatement.getExpression().getText();
    PsiSwitchStatement switchStatement = (PsiSwitchStatement)new CommentTracker().replaceAndRestoreComments(
      expressionStatement, "switch (" + valueToSwitch + ") {}");
    CodeStyleManager.getInstance(context.project()).reformat(switchStatement);

    PsiCodeBlock body = switchStatement.getBody();
    PsiJavaToken rBrace = body == null ? null : body.getRBrace();
    if (rBrace != null) {
      updater.moveCaretTo(rBrace);
      PsiFile file = body.getContainingFile();
      Document document = file.getFileDocument();
      PsiDocumentManager.getInstance(context.project()).doPostponedOperationsAndUnblockDocument(document);
      int lineEndOffset = document.getLineEndOffset(document.getLineNumber(updater.getCaretOffset()) - 1);
      updater.moveCaretTo(lineEndOffset);
    }
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiExpressionStatement expressionStatement) {
    boolean valid = expressionStatement.getParent() instanceof PsiCodeBlock &&
                    !(expressionStatement.getExpression() instanceof PsiAssignmentExpression) &&
                    !(expressionStatement.getExpression() instanceof PsiLiteralExpression) &&
                    PsiTreeUtil.findChildOfType(expressionStatement.getExpression(), PsiErrorElement.class) == null &&
                    isValidTypeForSwitch(expressionStatement.getExpression().getType(), expressionStatement);
    return valid ? Presentation.of(getFamilyName()).withPriority(PriorityAction.Priority.LOW) : null;
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
}
