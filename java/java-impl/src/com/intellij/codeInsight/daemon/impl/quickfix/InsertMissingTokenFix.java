// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class InsertMissingTokenFix implements IntentionActionWithFixAllOption {
  private final String myToken;

  public InsertMissingTokenFix(String token) {
    myToken = token;
  }

  @Override
  public @NotNull String getText() {
    return IdeBundle.message("quickfix.text.insert.0", myToken);
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             Editor editor,
                             PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    editor.getDocument().insertString(editor.getCaretModel().getOffset(), myToken);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public boolean belongsToMyFamily(@NotNull IntentionActionWithFixAllOption action) {
    return action instanceof InsertMissingTokenFix && ((InsertMissingTokenFix)action).myToken.equals(myToken);
  }
}
