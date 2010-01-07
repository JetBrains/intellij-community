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
package com.intellij.refactoring.rename;

import com.intellij.CommonBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.TitledHandler;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveDirectoryWithClassesProcessor;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class DirectoryAsPackageRenameHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler");

  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    return element instanceof PsiDirectory && ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInContent(((PsiDirectory)element).getVirtualFile());
  }

  public boolean isRenaming(final DataContext dataContext) {
    return isAvailableOnDataContext(dataContext);
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file, final DataContext dataContext) {
    PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    final PsiElement nameSuggestionContext = file.findElementAt(editor.getCaretModel().getOffset());
    doRename(element, project, nameSuggestionContext, editor);
  }

  public void invoke(@NotNull final Project project, @NotNull final PsiElement[] elements, final DataContext dataContext) {
    PsiElement element = elements.length == 1 ? elements[0] : null;
    if (element == null) element = PsiElementRenameHandler.getElement(dataContext);
    LOG.assertTrue(element != null);
    Editor editor = PlatformDataKeys.EDITOR.getData(dataContext);
    doRename(element, project, element, editor);
  }

  public static void doRename(PsiElement element, final Project project, PsiElement nameSuggestionContext, Editor editor) {
    final PsiDirectory psiDirectory = (PsiDirectory)element;
    final PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
    final String qualifiedName = aPackage != null ? aPackage.getQualifiedName() : "";
    if (aPackage == null || qualifiedName.length() == 0/*default package*/ ||
        !JavaPsiFacade.getInstance(project).getNameHelper().isIdentifier(psiDirectory.getName())) {
      PsiElementRenameHandler.rename(element, project, nameSuggestionContext, editor);
    }
    else {
      PsiDirectory[] directories = aPackage.getDirectories();
      final VirtualFile[] virtualFiles = aPackage.occursInPackagePrefixes();
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
          final String promptMessage = "Package \'" +
                                       aPackage.getName() +
                                       "\' contains directories in libraries which cannot be renamed. Do you want to rename current directory";
          if (projectDirectories.length > 0) {
            int ret = Messages
              .showDialog(project, promptMessage + " or all directories in project?", RefactoringBundle.message("warning.title"),
                          new String[]{RefactoringBundle.message("rename.current.directory"),
                            RefactoringBundle.message("rename.directories"), CommonBundle.getCancelButtonText()}, 0,
                          Messages.getWarningIcon());
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, ret == 0 ? new PsiDirectory[] {psiDirectory} : projectDirectories);
          }
          else {
            if (Messages.showDialog(project, promptMessage + "?", RefactoringBundle.message("warning.title"),
                                      new String[]{CommonBundle.getOkButtonText(), CommonBundle.getCancelButtonText()}, 0,
                                      Messages.getWarningIcon()) == DialogWrapper.OK_EXIT_CODE) {
              renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
            }
          }
        }
        else {
          final StringBuffer message = new StringBuffer();
          RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
          buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
          message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed",
                                                   psiDirectory.getVirtualFile().getPresentableUrl()));
          int ret = Messages.showDialog(project, message.toString(), RefactoringBundle.message("warning.title"),
                                    new String[]{RefactoringBundle.message("rename.package.button.text"),
                                      RefactoringBundle.message("rename.directory.button.text"), CommonBundle.getCancelButtonText()}, 0,
                                    Messages.getWarningIcon());
          if (ret == 0) {
            PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
          }
          else if (ret == 1) {
            renameDirs(project, nameSuggestionContext, editor, psiDirectory, aPackage, psiDirectory);
          }
        }
      }
    }
  }

  private static void renameDirs(final Project project,
                                 final PsiElement nameSuggestionContext,
                                 final Editor editor,
                                 final PsiDirectory contextDirectory,
                                 final PsiPackage aPackage,
                                 final PsiDirectory... dirsToRename) {
    final RenameDialog dialog = new RenameDialog(project, contextDirectory, nameSuggestionContext, editor) {
      @Override
      protected void doAction() {
        final String newName = StringUtil.getQualifiedName(StringUtil.getPackageName(aPackage.getQualifiedName()), getNewName());
        final MoveDirectoryWithClassesProcessor moveProcessor =
          new MoveDirectoryWithClassesProcessor(project, dirsToRename, null, isSearchInComments(), isSearchInNonJavaFiles(), false, null) {
            @Override
            public TargetDirectoryWrapper getTargetDirectory(final PsiDirectory dir) {
              return new TargetDirectoryWrapper(dir.getParentDirectory(), getNewName());
            }

            @Override
            protected String getTargetName() {
              return newName;
            }

            @Override
            protected String getCommandName() {
              return dirsToRename.length == 1 ? "Rename directory" : "Rename directories";
            }
          };
        invokeRefactoring(moveProcessor);
      }
    };
    dialog.show();
  }

  public static void buildMultipleDirectoriesInPackageMessage(StringBuffer message,
                                                              PsiPackage aPackage,
                                                              PsiDirectory[] directories) {
    message.append(RefactoringBundle.message("multiple.directories.correspond.to.package"));
    message.append(aPackage.getQualifiedName());
    message.append(" :\n\n");
    for (int i = 0; i < directories.length; i++) {
      PsiDirectory directory = directories[i];
      if (i > 0) {
        message.append("\n");
      }
      message.append(directory.getVirtualFile().getPresentableUrl());
    }
  }

  public String getActionTitle() {
    return RefactoringBundle.message("rename.directory.title");
  }
}
