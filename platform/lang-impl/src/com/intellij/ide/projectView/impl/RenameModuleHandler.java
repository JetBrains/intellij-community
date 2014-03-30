/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.projectView.impl;

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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidator;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */

public class RenameModuleHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.projectView.actions.RenameModuleHandler");

  @Override
  public boolean isAvailableOnDataContext(DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    return module != null;
  }

  @Override
  public boolean isRenaming(DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.assertTrue(false);
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull PsiElement[] elements, @NotNull DataContext dataContext) {
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    LOG.assertTrue(module != null);
    Messages.showInputDialog(project,
                             IdeBundle.message("prompt.enter.new.module.name"),
                             IdeBundle.message("title.rename.module"),
                             Messages.getQuestionIcon(),
                             module.getName(),
                             new MyInputValidator(project, module));
  }

  @Override
  public String getActionTitle() {
    return RefactoringBundle.message("rename.module.title");
  }

  private static class MyInputValidator implements InputValidator {
    private final Project myProject;
    private final Module myModule;
    public MyInputValidator(Project project, Module module) {
      myProject = project;
      myModule = module;
    }

    @Override
    public boolean checkInput(String inputString) {
      return inputString != null && inputString.length() > 0;
    }

    @Override
    public boolean canClose(final String inputString) {
      final String oldName = myModule.getName();
      final ModifiableModuleModel modifiableModel = renameModule(inputString);
      if (modifiableModel == null) return false;
      final Ref<Boolean> success = Ref.create(Boolean.TRUE);
      CommandProcessor.getInstance().executeCommand(myProject, new Runnable() {
        @Override
        public void run() {
          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
              modifiableModel.commit();
            }
          });
        }
      }, IdeBundle.message("command.renaming.module", oldName), null);
      return success.get().booleanValue();
    }

    @Nullable
    private ModifiableModuleModel renameModule(String inputString) {
      final ModifiableModuleModel modifiableModel = ModuleManager.getInstance(myProject).getModifiableModel();
      try {
        modifiableModel.renameModule(myModule, inputString);
      }
      catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
        Messages.showErrorDialog(myProject, IdeBundle.message("error.module.already.exists", inputString),
                                 IdeBundle.message("title.rename.module"));
        return null;
      }
      return modifiableModel;
    }
  }

}
