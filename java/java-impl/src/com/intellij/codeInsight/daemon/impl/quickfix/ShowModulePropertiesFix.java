/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ShowModulePropertiesFix implements QuickFix<CommonProblemDescriptor>, IntentionAction {
  private final String myModuleName;

  public ShowModulePropertiesFix(@NotNull PsiElement context) {
    this(ModuleUtilCore.findModuleForPsiElement(context));
  }

  public ShowModulePropertiesFix(@Nullable Module module) {
    myModuleName = module == null ? null : module.getName();
  }

  @NotNull
  @Override
  public String getName() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.MODULE_SETTINGS);
    return action.getTemplatePresentation().getText();
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getName();
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull CommonProblemDescriptor descriptor) {
    invoke(project, null, null);
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myModuleName != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(myModuleName, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}