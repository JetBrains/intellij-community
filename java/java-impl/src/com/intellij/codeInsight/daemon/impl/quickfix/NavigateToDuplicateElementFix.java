// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.JavaElementKind;
import org.jetbrains.annotations.NotNull;

public class NavigateToDuplicateElementFix extends PsiBasedModCommandAction<NavigatablePsiElement> {
  private final @IntentionName String myText;

  public NavigateToDuplicateElementFix(@NotNull NavigatablePsiElement element) {
    super(element);
    myText = QuickFixBundle.message("navigate.duplicate.element.text", JavaElementKind.fromElement(element).object());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return myText;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull NavigatablePsiElement element) {
    if (element instanceof PsiNameIdentifierOwner owner) {
      PsiElement identifier = owner.getNameIdentifier();
      if (identifier != null) {
        return ModCommand.select(identifier);
      }
    }
    return ModCommand.select(element);
  }
}
