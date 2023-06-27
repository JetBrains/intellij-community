// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInspection.PsiUpdateModCommandAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SetVariableTypeFix extends PsiUpdateModCommandAction<PsiVariable> {
  private final @NotNull SmartTypePointer myTypePointer;
  private final @NotNull @NlsSafe String myTypeText;

  public SetVariableTypeFix(PsiVariable variable, PsiType type) {
    super(variable);
    myTypeText = type.getPresentableText();
    myTypePointer = SmartTypePointerManager.getInstance(variable.getProject()).createSmartTypePointer(type);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiVariable variable, @NotNull ModPsiUpdater updater) {
    PsiType type = myTypePointer.getType();
    if (type == null) return;
    if (!(variable instanceof PsiReceiverParameter)) {
      JavaSharedImplUtil.normalizeBrackets(variable);
    }
    PsiTypeElement typeElement = variable.getTypeElement();
    if (typeElement == null) return;
    PsiTypeElement typeElementByExplicitType = JavaPsiFacade.getElementFactory(context.project()).createTypeElement(type);
    typeElement.replace(typeElementByExplicitType);
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiVariable element) {
    return myTypePointer.getType() == null ? null : Presentation.of(getText());
  }

  @NotNull
  protected @Nls String getText() {
    return JavaBundle.message("intention.name.set.variable.type", myTypeText);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.name.set.explicit.variable.type");
  }
}
