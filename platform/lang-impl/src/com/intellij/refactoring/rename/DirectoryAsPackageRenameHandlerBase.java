/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.refactoring.rename;

import com.intellij.CommonBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.file.PsiPackageBase;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public abstract class DirectoryAsPackageRenameHandlerBase<T extends PsiDirectoryContainer> implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler");

  protected abstract VirtualFile[] occursInPackagePrefixes(T aPackage);

  protected abstract boolean isIdentifier(String name, Project project);

  protected abstract String getQualifiedName(T aPackage);

  @Nullable
  protected abstract T getPackage(PsiDirectory psiDirectory);

  protected abstract BaseRefactoringProcessor createProcessor(final String newQName,
                                                              final Project project,
                                                              final PsiDirectory[] dirsToRename,
                                                              boolean searchInComments,
                                                              boolean searchInNonJavaFiles);

  @Override
  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    PsiElement element = adjustForRename(dataContext, PsiElementRenameHandler.getElement(dataContext));
    if (element instanceof PsiDirectory) {
      final VirtualFile virtualFile = ((PsiDirectory)element).getVirtualFile();
      final Project project = element.getProject();
      if (Comparing.equal(project.getBaseDir(), virtualFile)) return false;
      if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(virtualFile)) {
        return true;
      }
    }
    return false;
  }

  private static PsiElement adjustForRename(DataContext dataContext, PsiElement element) {
    if (element instanceof PsiDirectoryContainer) {
      final Module module = LangDataKeys.MODULE.getData(dataContext);
      if (module != null) {
        final PsiDirectory[] directories = ((PsiDirectoryContainer)element).getDirectories(GlobalSearchScope.moduleScope(module));
        if (directories.length >= 1) {
          element = directories[0];
        }
      }
    }
    return element;
  }

  @Override
  public boolean isRenaming(final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    PsiElement element = adjustForRename(dataContext, PsiElementRenameHandler.getElement(dataContext));
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    doRename(element, project, nameSuggestionContext, editor);
  }

  @Override
  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = PsiElementRenameHandler.getElement(dataContext);
    final PsiElement nameSuggestionContext = element;
    element = adjustForRename(dataContext, element);
    LOG.assertTrue(element != null);
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    doRename(element, project, nameSuggestionContext, editor);
  }

  private void doRename(PsiElement element, final Project project, PsiElement nameSuggestionContext, Editor editor) {
    final PsiDirectory psiDirectory = (PsiDirectory)element;
    final T aPackage = getPackage(psiDirectory);
    final String qualifiedName = aPackage != null ? getQualifiedName(aPackage) : "";
    if (aPackage == null || qualifiedName.length() == 0/*default package*/ ||
        !isIdentifier(psiDirectory.getName(), project)) {
      PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor);
    }
    else {
      PsiDirectory[] directories = aPackage.getDirectories();
      final VirtualFile[] virtualFiles = occursInPackagePrefixes(aPackage);
      if (virtualFiles.length == 0 && directories.length == 1) {
        PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
      }
      else { // the directory corresponds to a package that has multiple associated directories
        final ProjectFileIndex projectFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        boolean inLib = false;
        for (PsiDirectory directory : directories) {
          inLib |= !projectFileIndex.isInContent(directory.getVirtualFile());
        }

        final PsiDirectory[] projectDirectories = aPackage.getDirectories(GlobalSearchScope.projectScope(project));
        if (inLib) {
          final Module module = ModuleUtilCore.findModuleForPsiElement(psiDirectory);
          LOG.assertTrue(module != null);
          PsiDirectory[] moduleDirs = null;
          if (nameSuggestionContext instanceof PsiPackageBase) {
            moduleDirs = aPackage.getDirectories(GlobalSearchScope.moduleScope(module));
            if (moduleDirs.length <= 1) {
              moduleDirs = null;
            }
          }
          final String promptMessage = "Package \'" +
                                       aPackage.getName() +
                                       "\' contains directories in libraries which cannot be renamed. Do you want to rename " +
                                       (moduleDirs == null ? "current directory" : "current module directories");
          if (projectDirectories.length > 0) {
            int ret = Messages
              .showYesNoCancelDialog(project, promptMessage + " or all directories in project?", RefactoringBundle.message("warning.title"),
                          RefactoringBundle.message("rename.current.directory"),
                            RefactoringBundle.message("rename.directories"), CommonBundle.getCancelButtonText(),
                          Messages.getWarningIcon());
            if (ret == Messages.CANCEL) return;
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage,
                       ret == Messages.YES ? (moduleDirs == null ? new PsiDirectory[]{psiDirectory} : moduleDirs) : projectDirectories);
          }
          else {
            if (Messages.showOkCancelDialog(project, promptMessage + "?", RefactoringBundle.message("warning.title"),
                                    Messages.getWarningIcon()) == Messages.OK) {
              renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
            }
          }
        }
        else {
          final StringBuffer message = new StringBuffer();
          RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
          buildMultipleDirectoriesInPackageMessage(message, getQualifiedName(aPackage), directories);
          message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed",
                                                   psiDirectory.getVirtualFile().getPresentableUrl()));
          int ret = Messages.showYesNoCancelDialog(project, message.toString(), RefactoringBundle.message("warning.title"),
                                        RefactoringBundle.message("rename.package.button.text"),
                                          RefactoringBundle.message("rename.directory.button.text"), CommonBundle.getCancelButtonText(),
                                        Messages.getWarningIcon());
          if (ret == Messages.YES) {
            PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
          }
          else if (ret == Messages.NO) {
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
          }
        }
      }
    }
  }

  private void renameDirs(final Project project,
                          final PsiElement nameSuggestionContext,
                          final Editor editor,
                          final PsiDirectory contextDirectory,
                          final T aPackage,
                          final PsiDirectory... dirsToRename) {
    final RenameDialog dialog = new RenameDialog(project, contextDirectory, nameSuggestionContext, editor) {
      @Override
      protected void doAction() {
        String newQName = StringUtil.getQualifiedName(StringUtil.getPackageName(getQualifiedName(aPackage)), getNewName());
        BaseRefactoringProcessor moveProcessor = createProcessor(newQName, project, dirsToRename, isSearchInComments(),
                                                                 isSearchInNonJavaFiles());
        invokeRefactoring(moveProcessor);
      }
    };
    dialog.show();
  }

  public static void buildMultipleDirectoriesInPackageMessage(StringBuffer message,
                                                              String packageQname,
                                                              PsiDirectory[] directories) {
    message.append(RefactoringBundle.message("multiple.directories.correspond.to.package"));
    message.append(packageQname);
    message.append(":\n\n");
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (i > 0) {
        message.append("\n");
      }
      message.append(directory.getVirtualFile().getPresentableUrl());
    }
  }

  @Override
  public String getActionTitle() {
    return RefactoringBundle.message("rename.directory.title");
  }
}
