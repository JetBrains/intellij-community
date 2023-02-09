/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.scratch.ScratchUtil;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;

public class MoveFilesOrDirectoriesHandler extends MoveHandlerDelegate {
  private static final Logger LOG = Logger.getInstance(MoveFilesOrDirectoriesHandler.class);

  @Override
  public boolean canMove(final PsiElement[] elements, final PsiElement targetContainer, @Nullable PsiReference reference) {
    HashSet<String> names = new HashSet<>();
    for (PsiElement element : elements) {
      if (element instanceof PsiFile file) {
        String name = file.getName();
        if (names.contains(name)) {
          return false;
        }
        names.add(name);
      }
      else if (!(element instanceof PsiDirectory)) {
        return false;
      }
    }

    return super.canMove(elements, targetContainer, reference);
  }

  @Override
  public boolean isValidTarget(final PsiElement targetElement, PsiElement[] sources) {
    return isValidTarget(targetElement);
  }

  public static boolean isValidTarget(PsiElement psiElement) {
    if (psiElement == null) return true;
    if (!(psiElement instanceof PsiDirectory || psiElement instanceof PsiDirectoryContainer)) return false;
    if (psiElement.getManager().isInProject(psiElement)) return true;
    VirtualFile virtualFile = PsiUtilCore.getVirtualFile(psiElement);
    return ScratchUtil.isScratch(virtualFile) ||
           virtualFile != null && ProjectRootManager.getInstance(psiElement.getProject()).getFileIndex().isExcluded(virtualFile);
  }

  public void doMove(final PsiElement[] elements, final PsiElement targetContainer) {
    final Project project = targetContainer != null ? targetContainer.getProject() : elements[0].getProject();
    doMove(project, elements, targetContainer, null);
  }


  @Override
  public PsiElement @Nullable [] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    return PsiTreeUtil.filterAncestors(sourceElements);
  }

  @Override
  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, @Nullable final MoveCallback callback) {
    if (!LOG.assertTrue(targetContainer == null || targetContainer instanceof PsiDirectory || targetContainer instanceof PsiDirectoryContainer,
                        "container: " + targetContainer + "; elements: " + Arrays.toString(elements) + "; working handler: " + toString())) {
      return;
    }
    final PsiElement[] adjustedElements = adjustForMove(project, elements, targetContainer);
    if (adjustedElements != null) {
      MoveFilesOrDirectoriesUtil.doMove(project, adjustedElements, new PsiElement[] {targetContainer}, callback);
    }
  }

  @Override
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if ((element instanceof PsiFile && ((PsiFile)element).getVirtualFile() != null)
        || element instanceof PsiDirectory) {
      doMove(project, new PsiElement[]{element}, LangDataKeys.TARGET_PSI_ELEMENT.getData(dataContext), null);
      return true;
    }
    if (element instanceof PsiPlainText) {
      PsiFile file = element.getContainingFile();
      if (file != null) {
        doMove(project, new PsiElement[]{file}, null, null);
      }
      return true;
    }
    return false;
  }

  @Nullable
  @Override
  public String getActionName(PsiElement @NotNull [] elements) {
    int fileCount = 0, directoryCount = 0;
    for (PsiElement element : elements) {
      if (element instanceof PsiFile) {
        fileCount++;
      }
      else if (element instanceof PsiDirectory) {
        directoryCount++;
      }
    }
    if (directoryCount == 0) {
      return fileCount == 1 ? RefactoringBundle.message("move.file") : RefactoringBundle.message("move.files");
    }
    if (fileCount == 0) {
      return directoryCount == 1 ? RefactoringBundle.message("move.directory") : RefactoringBundle.message("move.directories.with.dialog");
    }
    return RefactoringBundle.message("move.files.and.directories");
  }
}
