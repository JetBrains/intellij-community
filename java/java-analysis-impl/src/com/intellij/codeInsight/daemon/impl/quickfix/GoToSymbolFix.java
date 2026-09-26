// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModNavigate;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.NavigatablePsiElement;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class GoToSymbolFix extends PsiBasedModCommandAction<NavigatablePsiElement> {
  private final @IntentionName String myMessage;

  public GoToSymbolFix(@NotNull NavigatablePsiElement symbol, @NotNull @Nls String message) {
    super(symbol);
    myMessage = message;
  }

  @Override
  public @NotNull String getFamilyName() {
    return myMessage;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull NavigatablePsiElement element) {
    return new ModNavigate(element.getContainingFile().getVirtualFile(), 0, 0, 0);
  }

  @Override
  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, NavigatablePsiElement element) {
    return IntentionPreviewInfo.navigate(element);
  }
}