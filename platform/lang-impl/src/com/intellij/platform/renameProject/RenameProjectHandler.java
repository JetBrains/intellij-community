// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.renameProject;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.platform.ModuleAttachProcessor;
import com.intellij.projectImport.ProjectAttachProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameProjectHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance(RenameProjectHandler.class);

  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    return isAvailable(dataContext);
  }

  static boolean isAvailable(DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    return module != null;
  }

  @Override
  public String getActionTitle() {
    return RefactoringBundle.message("rename.project.handler.title");
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.error("Project renaming should be never invoked from file");
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    LOG.assertTrue(project instanceof ProjectEx);

    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    LOG.assertTrue(module != null);
    Messages.showInputDialog(project, RefactoringBundle.message("enter.new.project.name"), RefactoringBundle.message("rename.project"),
                             Messages.getQuestionIcon(),
                             module.getName(),
                             new MyInputValidator((ProjectEx)project, module));
  }


  protected static class MyInputValidator implements InputValidator {
    private final ProjectEx myProject;
    @Nullable private final Module myModule;

    public MyInputValidator(ProjectEx project, @Nullable Module module) {
      myProject = project;
      myModule = module;
    }

    @Override
    public boolean checkInput(String inputString) {
      return inputString != null && inputString.length() > 0;
    }

    @Override
    public boolean canClose(final String inputString) {
      return renameProject(myProject, myModule, inputString);
    }
  }

  public static boolean renameProject(@NotNull ProjectEx project, @Nullable Module module, String newName){
    if (shouldRenameProject(project, module, newName)) {
      project.setProjectName(newName);
      project.save();
    }

    if (module != null && !newName.equals(module.getName())) {
      final ModifiableModuleModel modifiableModel = ModuleManager.getInstance(project).getModifiableModel();
      try {
        modifiableModel.renameModule(module, newName);
      }
      catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
        Messages.showErrorDialog(project, IdeBundle.message("error.module.already.exists", newName),
                                 IdeBundle.message("title.rename.module"));
        return false;
      }
      final Ref<Boolean> success = Ref.create(Boolean.TRUE);
      CommandProcessor.getInstance().executeCommand(project,
                                                    () -> ApplicationManager.getApplication().runWriteAction(() -> modifiableModel.commit()),
                                                    IdeBundle.message("command.renaming.module", module.getName()), null);
      return success.get().booleanValue();
    }
    return true;
  }

  private static boolean shouldRenameProject(@NotNull Project project, @Nullable Module module, String inputString) {
    if (inputString.equals(project.getName())) {
      return false;
    }

    if (module == null) {
      return true;
    }

    if (ProjectAttachProcessor.canAttachToProject()) {
      return module == ModuleAttachProcessor.getPrimaryModule(project);
    }

    return module == PrimaryModuleManager.findPrimaryModule(project);
  }
}