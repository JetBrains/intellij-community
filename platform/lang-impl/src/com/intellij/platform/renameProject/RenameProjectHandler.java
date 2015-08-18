/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.platform.renameProject;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.project.DumbModePermission;
import com.intellij.openapi.project.DumbService;
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

/**
 * @author lene
 *         Date: 30.09.11
 */
public class RenameProjectHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#" + RenameProjectHandler.class.getName());

  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    return isAvailable(dataContext);
  }

  static boolean isAvailable(DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    return module != null;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
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
  public void invoke(@NotNull Project project, @NotNull PsiElement[] elements, DataContext dataContext) {
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
      if (shouldRenameProject(inputString)) {
        myProject.setProjectName(inputString);
        myProject.save();
      }

      if (myModule != null && !inputString.equals(myModule.getName())) {
        final ModifiableModuleModel modifiableModel = ModuleManager.getInstance(myProject).getModifiableModel();
        try {
          modifiableModel.renameModule(myModule, inputString);
        }
        catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
          Messages.showErrorDialog(myProject, IdeBundle.message("error.module.already.exists", inputString),
                                   IdeBundle.message("title.rename.module"));
          return false;
        }
        final Ref<Boolean> success = Ref.create(Boolean.TRUE);
        CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
          @Override
          public void run() {
            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                DumbService.allowStartingDumbModeInside(DumbModePermission.MAY_START_BACKGROUND, new Runnable() {
                  @Override
                  public void run() {
                    modifiableModel.commit();
                  }
                });
              }
            });
          }
        }, IdeBundle.message("command.renaming.module", myModule.getName()), null);
        return success.get().booleanValue();
      }
      return true;
    }

    private boolean shouldRenameProject(String inputString) {
      if (inputString.equals(myProject.getName())) {
        return false;
      }

      if (myModule == null) {
        return true;
      }

      if (ProjectAttachProcessor.canAttachToProject()) {
        return myModule == ModuleAttachProcessor.getPrimaryModule(myProject);
      }

      return myModule == ModuleAttachProcessor.findModuleInBaseDir(myProject);
    }
  }
}
