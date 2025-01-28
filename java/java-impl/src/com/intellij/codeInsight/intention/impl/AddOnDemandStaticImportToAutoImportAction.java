// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class AddOnDemandStaticImportToAutoImportAction extends PsiBasedModCommandAction<PsiImportStaticStatement> {

  private AddOnDemandStaticImportToAutoImportAction() {
    super(PsiImportStaticStatement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.add.on.demand.static.import.to.auto.import.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiImportStaticStatement element) {
    String nameToImport = getNameToImport(element);
    if (nameToImport == null) return null;
    return Presentation.of(JavaBundle.message("intention.add.on.demand.static.import.to.auto.import.text", nameToImport));
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiImportStaticStatement element) {
    String nameToImport = getNameToImport(element);
    if (nameToImport == null) return ModCommand.nop();
    return ModCommand.updateOptionList(context.file(), "JavaProjectCodeInsightSettings.includedAutoStaticNames",
                                       strings -> strings.add(nameToImport));
  }

  @Nullable
  private static String getNameToImport(@NotNull PsiImportStaticStatement element) {
    if (!element.isOnDemand()) return null;
    PsiJavaCodeReferenceElement importReference = element.getImportReference();
    if (importReference == null) return null;
    String name = importReference.getCanonicalText();
    if (JavaCodeStyleManager.getInstance(element.getProject()).isStaticAutoImportClass(name)) return null;
    return name;
  }
}
