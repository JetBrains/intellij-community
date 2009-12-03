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
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.lang.TitledHandler;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class DirectoryAsPackageRenameHandler implements RenameHandler, TitledHandler {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.rename.DirectoryAsPackageRenameHandler");

  public boolean isAvailableOnDataContext(final DataContext dataContext) {
    final PsiElement element = PsiElementRenameHandler.getElement(dataContext);
    return element instanceof PsiDirectory;
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

  public static void doRename(PsiElement element, Project project, PsiElement nameSuggestionContext, Editor editor) {
    PsiDirectory psiDirectory = (PsiDirectory)element;
    PsiPackage aPackage = JavaDirectoryService.getInstance().getPackage(psiDirectory);
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
        StringBuffer message = new StringBuffer();
        RenameUtil.buildPackagePrefixChangedMessage(virtualFiles, message, qualifiedName);
        buildMultipleDirectoriesInPackageMessage(message, aPackage, directories);
        message.append(RefactoringBundle.message("directories.and.all.references.to.package.will.be.renamed", psiDirectory.getVirtualFile().getPresentableUrl()));
        int ret =
          Messages.showDialog(project, message.toString(), RefactoringBundle.message("warning.title"),
                              new String[]{
                                RefactoringBundle.message("rename.package.button.text"),
                                RefactoringBundle.message("rename.directory.button.text"),
                              CommonBundle.getCancelButtonText()}, 0, Messages.getWarningIcon());
        if (ret == 0) {
          PsiElementRenameHandler.rename(aPackage, project, nameSuggestionContext, editor);
        } else if (ret == 1){
          PsiElementRenameHandler.rename(psiDirectory, project, nameSuggestionContext, editor);
        }
      }
    }
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
