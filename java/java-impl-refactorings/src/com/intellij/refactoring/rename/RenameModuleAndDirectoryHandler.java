// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleWithNameAlreadyExists;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

public class RenameModuleAndDirectoryHandler implements RenameHandler, TitledHandler {

  private static final Logger LOG = Logger.getInstance(RenameModuleAndDirectoryHandler.class);

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    LOG.error("Must not be called");
  }

  @Override
  public String getActionTitle() {
    return JavaRefactoringBundle.message("rename.module.directory.title");
  }

  /**
   * @see com.intellij.ide.projectView.impl.RenameModuleHandler#isAvailableOnDataContext(DataContext)
   * @see PlainDirectoryRenameHandler#isAvailableOnDataContext(DataContext)
   */
  @Override
  public boolean isAvailableOnDataContext(@NotNull DataContext dataContext) {
    Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    if (module == null) {
      return false;
    }
    PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    if (!(element instanceof PsiDirectory)) {
      return false;
    }
    PsiDirectory directory = (PsiDirectory)element;
    //noinspection deprecation
    if (directory.getVirtualFile().equals(directory.getProject().getBaseDir())) {
      // this is project root, we can't rename project root yet
      return false;
    }
    if (!PlainDirectoryRenameHandler.isPlainDirectory(directory)) {
      // this is a package
      return false;
    }
    if (RenamePsiElementProcessor.forElement(directory).getClass() != RenamePsiDirectoryProcessor.class) {
      // another plugin overrides our processor
      // we cannot deal with it properly
      return false;
    }
    return module.getName().equals(directory.getName());
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    final Module module = LangDataKeys.MODULE_CONTEXT.getData(dataContext);
    LOG.assertTrue(module != null);
    final PsiElement element = elements.length == 1 ? elements[0] : PsiElementRenameHandler.getElement(dataContext);
    LOG.assertTrue(element != null);
    PsiElementRenameHandler.rename(
      element, project,
      element,
      CommonDataKeys.EDITOR.getData(dataContext),
      dataContext.getData(PsiElementRenameHandler.DEFAULT_NAME),
      new RenameModuleAndDirectoryProcessor(module)
    );
  }

  private static final class RenameModuleAndDirectoryProcessor extends RenamePsiDirectoryProcessor {

    private final Module myModule;

    private RenameModuleAndDirectoryProcessor(@NotNull Module module) {
      myModule = module;
    }

    @NotNull
    @Override
    public RenameDialog createRenameDialog(@NotNull Project project,
                                           @NotNull PsiElement element,
                                           PsiElement nameSuggestionContext,
                                           Editor editor) {
      return new RenameWithOptionalReferencesDialog(project, element, nameSuggestionContext, editor) {

        @Override
        protected boolean getSearchForReferences() {
          return RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY;
        }

        @Override
        protected void setSearchForReferences(boolean value) {
          RefactoringSettings.getInstance().RENAME_SEARCH_FOR_REFERENCES_FOR_DIRECTORY = value;
        }

        @Override
        protected void canRun() throws ConfigurationException {
          super.canRun();
          checkRenameModule(myModule, getNewName());
        }

        @Override
        protected RenameProcessor createRenameProcessor(@NotNull String newName) {
          return new RenameProcessor(
            getProject(), getPsiElement(), newName, getRefactoringScope(),
            isSearchInComments(), isSearchInNonJavaFiles()
          ) {
            @Override
            protected void performRefactoringInBranch(UsageInfo @NotNull [] usages, @NotNull ModelBranch branch) {
              branch.runAfterMerge(() -> renameModule(myModule, newName));
              super.performRefactoringInBranch(usages, branch);
            }

            @Override
            public void performRefactoring(UsageInfo @NotNull [] usages) {
              renameModule(myModule, newName);
              super.performRefactoring(usages);
            }

            @NotNull
            @Override
            protected String getCommandName() {
              return JavaRefactoringBundle.message("rename.module.directory.command", newName);
            }
          };
        }
      };
    }
  }

  private static void checkRenameModule(@NotNull Module module, @NotNull String newName) throws ConfigurationException {
    final ModifiableModuleModel modifiableModel = ModuleManager.getInstance(module.getProject()).getModifiableModel();
    try {
      modifiableModel.renameModule(module, newName);
    }
    catch (ModuleWithNameAlreadyExists moduleWithNameAlreadyExists) {
      throw new ConfigurationException(IdeBundle.message("error.module.already.exists", newName));
    }
  }

  private static void renameModule(@NotNull Module module, @NotNull String newName) {
    if (module.isDisposed()) {
      // happens if module gets removed while Usages Preview is shown
    }
    else {
      try {
        final ModifiableModuleModel model = ModuleManager.getInstance(module.getProject()).getModifiableModel();
        model.renameModule(module, newName);
        model.commit();
      }
      catch (ModuleWithNameAlreadyExists ignored) {
        // happens if another module gets renamed while Usages Preview is shown
      }
    }
  }
}