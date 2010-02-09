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
package com.intellij.refactoring.copy;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.daemon.impl.CollectHighlightsUtil;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditorHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class CopyClassesHandler implements CopyHandlerDelegate {
  public boolean canCopy(PsiElement[] elements) {
    return canCopyClass(elements);
  }

  public static boolean canCopyClass(PsiElement... elements) {
    return convertToTopLevelClass(elements) != null;
  }

  @Nullable
  private static PsiClass convertToTopLevelClass(final PsiElement[] elements) {
    if (elements.length == 1 && !CollectHighlightsUtil.isOutsideSourceRootJavaFile(elements[0].getContainingFile())) {
      return getTopLevelClass(elements [0]);
    }
    return null;
  }

  public void doCopy(PsiElement[] elements, PsiDirectory defaultTargetDirectory) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass aClass = convertToTopLevelClass(elements);
    assert aClass != null;
    if (defaultTargetDirectory == null) {
      defaultTargetDirectory = aClass.getContainingFile().getContainingDirectory();
    }
    Project project = defaultTargetDirectory.getProject();
    CopyClassDialog dialog = new CopyClassDialog(aClass, defaultTargetDirectory, project, false);
    dialog.setTitle(RefactoringBundle.message("copy.handler.copy.class"));
    dialog.show();
    if (dialog.isOK()) {
      PsiDirectory targetDirectory = dialog.getTargetDirectory();
      String className = dialog.getClassName();
      copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.copy.class"), false);
    }
  }

  public void doClone(PsiElement element) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.copyClass");
    PsiClass aClass = getTopLevelClass(element);
    Project project = element.getProject();

    CopyClassDialog dialog = new CopyClassDialog(aClass, null, project, true);
    dialog.setTitle(RefactoringBundle.message("copy.handler.clone.class"));
    dialog.show();
    if (dialog.isOK()) {
      String className = dialog.getClassName();
      PsiDirectory targetDirectory = element.getContainingFile().getContainingDirectory();
      copyClassImpl(className, project, aClass, targetDirectory, RefactoringBundle.message("copy.handler.clone.class"), true);
    }
  }

  private static void copyClassImpl(final String copyClassName, final Project project, final PsiClass aClass, final PsiDirectory targetDirectory, String commandName, final boolean selectInActivePanel) {
    if (copyClassName == null || copyClassName.length() == 0) return;
    final boolean[] result = new boolean[] {false};
    Runnable command = new Runnable() {
      public void run() {
        final Runnable action = new Runnable() {
          public void run() {
            try {
              PsiElement newElement = doCopyClass(aClass, copyClassName, targetDirectory);
              CopyHandler.updateSelectionInActiveProjectView(newElement, project, selectInActivePanel);
              EditorHelper.openInEditor(newElement);

              result[0] = true;
            }
            catch (final IncorrectOperationException ex) {
              ApplicationManager.getApplication().invokeLater(new Runnable() {
                public void run() {
                  Messages.showMessageDialog(project, ex.getMessage(), RefactoringBundle.message("error.title"), Messages.getErrorIcon());
                }
              });
            }
          }
        };
        ApplicationManager.getApplication().runWriteAction(action);
      }
    };
    CommandProcessor processor = CommandProcessor.getInstance();
    processor.executeCommand(project, command, commandName, null);

    if (result[0]) {
      ToolWindowManager.getInstance(project).invokeLater(new Runnable() {
        public void run() {
          ToolWindowManager.getInstance(project).activateEditorComponent();
        }
      });
    }
  }

  public static PsiElement doCopyClass(final PsiClass aClass, final String copyClassName, final PsiDirectory targetDirectory)
      throws IncorrectOperationException {
    PsiElement elementToCopy = aClass.getNavigationElement();

    final PsiClass classCopy = (PsiClass)elementToCopy.copy();
    classCopy.setName(copyClassName);

    final String fileName = copyClassName + "." + elementToCopy.getContainingFile().getOriginalFile().getViewProvider().getVirtualFile().getExtension();
    final PsiFile createdFile = targetDirectory.copyFileFrom(fileName, elementToCopy.getContainingFile());
    PsiElement newElement = createdFile;
    if (createdFile instanceof PsiClassOwner) {
      for (final PsiClass psiClass : ((PsiClassOwner)createdFile).getClasses()) {
        if (!(psiClass instanceof SyntheticElement)) {
          psiClass.getParent().deleteChildRange(psiClass, psiClass);
        }
      }

      final PsiClass newClass = (PsiClass)createdFile.add(classCopy);

      for (final PsiReference reference : ReferencesSearch.search(aClass, new LocalSearchScope(newClass)).findAll()) {
        reference.bindToElement(newClass);
      }

      new OptimizeImportsProcessor(aClass.getProject(), createdFile).run();

      newElement = newClass;
    }
    return newElement;
  }

  @Nullable
  private static PsiClass getTopLevelClass(PsiElement element) {
    while (true) {
      if (element == null || element instanceof PsiFile) break;
      if (element instanceof PsiClass && element.getParent() instanceof PsiFile) break;
      element = element.getParent();
    }
    if (element instanceof PsiClassOwner) {
      PsiClass[] classes = ((PsiClassOwner)element).getClasses();
      if (classes.length > 0) {
        for (final PsiClass aClass : classes) {
          if (aClass instanceof SyntheticElement) {
            return null;
          }
        }

        element = classes[0];
      }
    }
    return element instanceof PsiClass ? (PsiClass)element : null;
  }
}
