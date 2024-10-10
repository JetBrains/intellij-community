// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowModulePropertiesFix implements QuickFix<CommonProblemDescriptor>, IntentionAction, LowPriorityAction, DumbAware {
  private final String myModuleName;

  public ShowModulePropertiesFix(@NotNull PsiElement context) {
    this(ModuleUtilCore.findModuleForPsiElement(context));
  }

  public ShowModulePropertiesFix(@Nullable Module module) {
    myModuleName = module == null ? null : module.getName();
  }

  @Override
  public @NotNull String getName() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.MODULE_SETTINGS);
    return action.getTemplatePresentation().getText();
  }

  @Override
  public @Nls @NotNull String getText() {
    return getName();
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
    invoke(project, null, null);
  }

  @Override
  public boolean isAvailable(final @NotNull Project project, final Editor editor, final PsiFile file) {
    return myModuleName != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(myModuleName, null);
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return new IntentionPreviewInfo.Html(JavaBundle.message("open.settings.dialog.for.module.preview.text", myModuleName));
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}