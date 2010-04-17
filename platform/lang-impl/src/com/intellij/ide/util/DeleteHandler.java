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

package com.intellij.ide.util;

import com.intellij.CommonBundle;
import com.intellij.history.LocalHistory;
import com.intellij.history.LocalHistoryAction;
import com.intellij.ide.DataManager;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ex.MessagesEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.io.ReadOnlyAttributeUtil;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class DeleteHandler {
  private DeleteHandler() {
  }

  public static class DefaultDeleteProvider implements DeleteProvider {
    public boolean canDeleteElement(DataContext dataContext) {
      if (PlatformDataKeys.PROJECT.getData(dataContext) == null) {
        return false;
      }
      final PsiElement[] elements = getPsiElements(dataContext);
      return elements != null && shouldEnableDeleteAction(elements);
    }

    @Nullable
    private static PsiElement[] getPsiElements(DataContext dataContext) {
      PsiElement[] elements = LangDataKeys.PSI_ELEMENT_ARRAY.getData(dataContext);
      if (elements == null) {
        final Object data = LangDataKeys.PSI_ELEMENT.getData(dataContext);
        if (data != null) {
          elements = new PsiElement[]{(PsiElement)data};
        }
        else {
          final Object data1 = LangDataKeys.PSI_FILE.getData(dataContext);
          if (data1 != null) {
            elements = new PsiElement[]{(PsiFile)data1};
          }
        }
      }
      return elements;
    }

    public void deleteElement(DataContext dataContext) {
      PsiElement[] elements = getPsiElements(dataContext);
      if (elements == null) return;
      Project project = PlatformDataKeys.PROJECT.getData(dataContext);
      if (project == null) return;
      LocalHistoryAction a = LocalHistory.getInstance().startAction(IdeBundle.message("progress.deleting"));
      try {
        deletePsiElement(elements, project);
      }
      finally {
        a.finish();
      }
    }
  }

  public static void deletePsiElement(final PsiElement[] elementsToDelete, final Project project) {
    if (elementsToDelete == null || elementsToDelete.length == 0) return;

    final PsiElement[] elements = PsiTreeUtil.filterAncestors(elementsToDelete);

    boolean safeDeleteApplicable = true;
    for (int i = 0; i < elements.length && safeDeleteApplicable; i++) {
      PsiElement element = elements[i];
      safeDeleteApplicable = SafeDeleteProcessor.validElement(element);
    }

    final boolean dumb = DumbService.getInstance(project).isDumb();
    if (safeDeleteApplicable && !dumb) {
      DeleteDialog dialog = new DeleteDialog(project, elements, new DeleteDialog.Callback() {
        public void run(final DeleteDialog dialog) {
          if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(elements))) return;
          SafeDeleteProcessor.createInstance(project, new Runnable() {
            public void run() {
              dialog.close(DeleteDialog.CANCEL_EXIT_CODE);
            }
          }, elements, dialog.isSearchInComments(), dialog.isSearchInNonJava(), true).run();
        }
      });
      dialog.show();
      if (!dialog.isOK()) return;
    }
    else {
      @SuppressWarnings({"UnresolvedPropertyKey"})
      String warningMessage = DeleteUtil.generateWarningMessage(IdeBundle.message("prompt.delete.elements"), elements);

      boolean anyDirectories = false;
      String directoryName = null;
      for (PsiElement psiElement : elementsToDelete) {
        if (psiElement instanceof PsiDirectory) {
          anyDirectories = true;
          directoryName = ((PsiDirectory)psiElement).getName();
          break;
        }
      }
      if (anyDirectories) {
        if (elements.length == 1) {
          warningMessage += IdeBundle.message("warning.delete.all.files.and.subdirectories", directoryName);
        }
        else {
          warningMessage += IdeBundle.message("warning.delete.all.files.and.subdirectories.in.the.selected.directory");
        }
      }

      if (safeDeleteApplicable && dumb) {
        warningMessage += "\n\nWarning:\n  Safe delete is not available while IntelliJ IDEA updates indices,\n  no usages will be checked.";
      }

      int result = Messages.showDialog(project, warningMessage, IdeBundle.message("title.delete"),
                                       new String[]{CommonBundle.getOkButtonText(), CommonBundle.getCancelButtonText()}, 0,
                                       Messages.getQuestionIcon());
      if (result != 0) return;
    }

    final FileTypeManager ftManager = FileTypeManager.getInstance();
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, Arrays.asList(elements), false);

        // deleted from project view or something like that.
        if (PlatformDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext()) == null) {
          CommandProcessor.getInstance().markCurrentCommandAsGlobal(project);
        }

        for (final PsiElement elementToDelete : elements) {
          if (!elementToDelete.isValid()) continue; //was already deleted
          if (elementToDelete instanceof PsiDirectory) {
            VirtualFile virtualFile = ((PsiDirectory)elementToDelete).getVirtualFile();
            if (virtualFile.isInLocalFileSystem()) {

              ArrayList<VirtualFile> readOnlyFiles = new ArrayList<VirtualFile>();
              getReadOnlyVirtualFiles(virtualFile, readOnlyFiles, ftManager);

              if (readOnlyFiles.size() > 0) {
                int _result = Messages.showYesNoDialog(project, IdeBundle.message("prompt.directory.contains.read.only.files",
                                                                                  virtualFile.getPresentableUrl()),
                                                       IdeBundle.message("title.delete"), Messages.getQuestionIcon());
                if (_result != 0) continue;

                boolean success = true;
                for (VirtualFile file : readOnlyFiles) {
                  success = clearReadOnlyFlag(file, project);
                  if (!success) break;
                }
                if (!success) continue;
              }
            }
          }
          else if (!elementToDelete.isWritable()) {
            final PsiFile file = elementToDelete.getContainingFile();
            if (file != null) {
              final VirtualFile virtualFile = file.getVirtualFile();
              if (virtualFile.isInLocalFileSystem()) {
                int _result = MessagesEx.fileIsReadOnly(project, virtualFile)
                  .setTitle(IdeBundle.message("title.delete"))
                  .appendMessage(IdeBundle.message("prompt.delete.it.anyway"))
                  .askYesNo();
                if (_result != 0) continue;

                boolean success = clearReadOnlyFlag(virtualFile, project);
                if (!success) continue;
              }
            }
          }

          try {
            elementToDelete.checkDelete();
          }
          catch (IncorrectOperationException ex) {
            Messages.showMessageDialog(project, ex.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
            continue;
          }

          ApplicationManager.getApplication().runWriteAction(new Runnable() {
            public void run() {
              try {
                elementToDelete.delete();
              }
              catch (final IncorrectOperationException ex) {
                ApplicationManager.getApplication().invokeLater(new Runnable() {
                  public void run() {
                    Messages.showMessageDialog(project, ex.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
                  }
                });
              }
            }
          });
        }
      }
    }, RefactoringBundle.message("safe.delete.command", RefactoringUIUtil.calculatePsiElementDescriptionList(elements)), null);
  }

  private static boolean clearReadOnlyFlag(final VirtualFile virtualFile, final Project project) {
    final boolean[] success = new boolean[1];
    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        Runnable action = new Runnable() {
          public void run() {
            try {
              ReadOnlyAttributeUtil.setReadOnlyAttribute(virtualFile, false);
              success[0] = true;
            }
            catch (IOException e1) {
              Messages.showMessageDialog(project, e1.getMessage(), CommonBundle.getErrorTitle(), Messages.getErrorIcon());
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    }, "", null);
    return success[0];
  }

  /**
   * Fills readOnlyFiles with VirtualFiles
   */
  private static void getReadOnlyVirtualFiles(VirtualFile file, ArrayList<VirtualFile> readOnlyFiles, final FileTypeManager ftManager) {
    if (ftManager.isFileIgnored(file.getName())) return;
    if (!file.isWritable()) {
      readOnlyFiles.add(file);
    }
    if (file.isDirectory()) {
      VirtualFile[] children = file.getChildren();
      for (VirtualFile child : children) {
        getReadOnlyVirtualFiles(child, readOnlyFiles, ftManager);
      }
    }
  }

  public static boolean shouldEnableDeleteAction(PsiElement[] elements) {
    if (elements == null || elements.length == 0) return false;
    for (PsiElement element : elements) {
      if (element instanceof PsiCompiledElement) {
        return false;
      }
    }
    return true;
  }
}
