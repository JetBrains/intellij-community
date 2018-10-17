// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class AddFinallyFix extends BaseIntentionAction {
  private final PsiTryStatement myTryStatement;

  public AddFinallyFix(PsiTryStatement statement) {
    myTryStatement = statement;
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("add.finally.block.family");
  }

  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!myTryStatement.isValid()) return false;
    if (myTryStatement.getFinallyBlock() != null) return false;
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiStatement replacement =
      JavaPsiFacade.getElementFactory(project).createStatementFromText(myTryStatement.getText() + "finally {}", myTryStatement);
    myTryStatement.replace(replacement);
  }
}
