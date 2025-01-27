// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.JavaProjectCodeInsightSettings;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.codeInspection.options.OptionController;
import com.intellij.codeInspection.options.OptionControllerProvider;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStaticStatement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static java.util.Objects.*;

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
    return ModCommand.updateOptionList(context.file(), "AutoImport.autoStaticImportTable", strings -> strings.add(
      requireNonNull(nameToImport)));
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

  /**
   * Provides an option "AutoImport.autoStaticImportTable" to change module-specific compiler options
   */
  @SuppressWarnings({"InjectedReferences", "LanguageMismatch"})
  public static final class AutoStaticImportTableProvider implements OptionControllerProvider {
    @Override
    public @NotNull OptionController forContext(@NotNull PsiElement context) {
      Project project = context.getProject();
      JavaProjectCodeInsightSettings codeInsightSettings = JavaProjectCodeInsightSettings.getSettings(project);
      String bindId = "autoStaticImportTable";
      return OptionController.empty()
        .onValue(bindId,
                 () -> codeInsightSettings.includedAutoStaticNames,
                 value -> {
                   codeInsightSettings.includedAutoStaticNames = value;
                 })
        .withRootPane(
          () -> OptPane.pane(OptPane.stringList(bindId, JavaBundle.message("auto.static.import.comment") + " " +
                                                        JavaBundle.message("auto.static.import.comment.2"))));
    }

    @Override
    public @NotNull String name() {
      return "AutoImport";
    }
  }
}
