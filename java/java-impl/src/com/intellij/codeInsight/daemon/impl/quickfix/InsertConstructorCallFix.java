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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class InsertConstructorCallFix implements IntentionActionWithFixAllOption, HighPriorityAction {
  protected final PsiMethod myConstructor;
  private final String myCall;

  public InsertConstructorCallFix(@NotNull PsiMethod constructor, @NonNls String call) {
    myConstructor = constructor;
    myCall = call;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("insert.super.constructor.call.text", myCall);
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
           && BaseIntentionAction.canModify(myConstructor)
    ;
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myConstructor;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiStatement superCall =
      JavaPsiFacade.getElementFactory(myConstructor.getProject()).createStatementFromText(myCall,null);

    PsiCodeBlock body = myConstructor.getBody();
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
