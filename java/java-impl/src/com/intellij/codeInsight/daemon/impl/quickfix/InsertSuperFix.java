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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiMatcherImpl;
import com.intellij.psi.util.PsiMatchers;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class InsertSuperFix implements IntentionAction, HighPriorityAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.InsertSuperFix");

  private final PsiMethod myConstructor;

  public InsertSuperFix(@NotNull PsiMethod constructor) {
    myConstructor = constructor;
  }

  @Override
  @NotNull
  public String getText() {
    return QuickFixBundle.message("insert.super.constructor.call.text");
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
        && myConstructor.getManager().isInProject(myConstructor)
    ;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(myConstructor.getContainingFile())) return;
    try {
      PsiStatement superCall =
        JavaPsiFacade.getInstance(myConstructor.getProject()).getElementFactory().createStatementFromText("super();",null);

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
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

}
