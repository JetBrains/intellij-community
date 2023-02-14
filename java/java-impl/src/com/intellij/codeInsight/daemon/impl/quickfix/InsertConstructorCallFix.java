// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class InsertConstructorCallFix implements IntentionActionWithFixAllOption, HighPriorityAction {
  protected final PsiMethod myConstructor;
  private final String myCall;

  public InsertConstructorCallFix(@NotNull PsiMethod constructor, @NonNls String call) {
    myConstructor = constructor;
    myCall = call;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new InsertConstructorCallFix(PsiTreeUtil.findSameElementInCopy(myConstructor, target), myCall);
  }

  @Override
  @NotNull
  public String getText() {
    return CommonQuickFixBundle.message("fix.insert.x", myCall);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("insert.super.constructor.call.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myConstructor.isValid()
           && myConstructor.getBody() != null
           && myConstructor.getBody().getLBrace() != null
           && BaseIntentionAction.canModify(myConstructor);
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myConstructor.getContainingFile();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiStatement superCall =
      JavaPsiFacade.getElementFactory(myConstructor.getProject()).createStatementFromText(myCall,null);

    PsiCodeBlock body = Objects.requireNonNull(myConstructor.getBody());
    PsiJavaToken lBrace = body.getLBrace();
    body.addAfter(superCall, lBrace);
    lBrace = (PsiJavaToken) new PsiMatcherImpl(body)
              .firstChild(PsiMatchers.hasClass(PsiExpressionStatement.class))
              .firstChild(PsiMatchers.hasClass(PsiMethodCallExpression.class))
              .firstChild(PsiMatchers.hasClass(PsiExpressionList.class))
              .firstChild(PsiMatchers.hasClass(PsiJavaToken.class))
              .dot(PsiMatchers.hasText("("))
              .getElement();
    editor.getCaretModel().moveToOffset(lBrace.getTextOffset()+1);
    UndoUtil.markPsiFileForUndo(file);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
