// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class GoToSymbolFix implements IntentionAction {
  private final SmartPsiElementPointer<NavigatablePsiElement> myPointer;
  private final @IntentionName String myMessage;

  public GoToSymbolFix(@NotNull NavigatablePsiElement symbol, @NotNull @Nls String message) {
    myPointer = SmartPointerManager.getInstance(symbol.getProject()).createSmartPsiElementPointer(symbol);
    myMessage = message;
  }

  @NotNull
  @Override
  public String getText() {
    return myMessage;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myPointer.getElement() != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    NavigatablePsiElement e = myPointer.getElement();
    if (e != null && e.isValid()) {
      e.navigate(true);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    NavigatablePsiElement element = myPointer.getElement();
    if (element == null) return IntentionPreviewInfo.EMPTY;
    return IntentionPreviewInfo.navigate(element);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}