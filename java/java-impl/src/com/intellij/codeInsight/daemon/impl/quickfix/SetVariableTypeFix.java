// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetVariableTypeFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  @SafeFieldForPreview
  private final @NotNull SmartTypePointer myTypePointer;
  private final @NotNull @NlsSafe String myTypeText;

  public SetVariableTypeFix(PsiVariable variable, PsiType type) {
    super(variable);
    myTypeText = type.getPresentableText();
    myTypePointer = SmartTypePointerManager.getInstance(variable.getProject()).createSmartTypePointer(type);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiType type = myTypePointer.getType();
    if (type == null) return;
    PsiVariable variable = (PsiVariable)startElement;
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) return;
    PsiTypeElement typeElementByExplicitType = JavaPsiFacade.getElementFactory(project).createTypeElement(type);
    typeElement.replace(typeElementByExplicitType);
  }

  @Override
  protected boolean isAvailable() {
    return super.isAvailable() && myTypePointer.getType() != null;
  }

  @Override
  public @NotNull String getText() {
    return JavaBundle.message("intention.name.set.variable.type", myTypeText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.set.explicit.variable.type");
  }
}
