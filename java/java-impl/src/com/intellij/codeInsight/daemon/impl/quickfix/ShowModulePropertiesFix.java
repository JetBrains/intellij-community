package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ProjectSettingsService;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ShowModulePropertiesFix implements IntentionAction {
  private final String myModuleName;

  public ShowModulePropertiesFix(String moduleName) {
    myModuleName = moduleName;
  }

  public ShowModulePropertiesFix(PsiElement context) {
    Module module = ModuleUtil.findModuleForPsiElement(context);
    myModuleName = module != null ? module.getName() : null;
  }

  @NotNull
  public String getText() {
    AnAction action = ActionManager.getInstance().getAction(IdeActions.MODULE_SETTINGS);
    return action.getTemplatePresentation().getText();
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myModuleName != null;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    ProjectSettingsService.getInstance(project).showModuleConfigurationDialog(myModuleName, null, false);
  }

  public boolean startInWriteAction() {
    return false;
  }
}