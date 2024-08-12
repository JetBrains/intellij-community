// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.ide.projectView.impl.NestingTreeStructureProvider;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public final class MoveRelatedFilesHandler extends MoveFilesOrDirectoriesHandler {
  @Override
  public boolean canMove(PsiElement[] elements, PsiElement targetContainer, @Nullable PsiReference reference) {
    if (!super.canMove(elements, targetContainer, reference)) return false;

    for (PsiElement element : elements) {
      if (element instanceof PsiFile &&
          ((PsiFile)element).getVirtualFile() != null &&
          !NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(element.getProject(),
                                                                             ((PsiFile)element).getVirtualFile()).isEmpty()) {
        return true;
      }
    }

    return false;
  }

  @Override
  public PsiElement @Nullable [] adjustForMove(final @NotNull Project project,
                                               PsiElement @NotNull [] sourceElements,
                                               final @Nullable PsiElement targetElement) {
    sourceElements = super.adjustForMove(project, sourceElements, targetElement);
    if (sourceElements == null) return null;

    final Set<PsiFile> relatedFilesToMove = new HashSet<>();

    for (PsiElement element : sourceElements) {
      if (!(element instanceof PsiFile)) continue;

      final VirtualFile file = ((PsiFile)element).getVirtualFile();
      if (file == null) continue;

      final Collection<NestingTreeStructureProvider.ChildFileInfo> relatedFileInfos =
        NestingTreeStructureProvider.getFilesShownAsChildrenInProjectView(element.getProject(), file);

      for (NestingTreeStructureProvider.ChildFileInfo info : relatedFileInfos) {
        final PsiFile psiFile = element.getManager().findFile(info.file());
        if (psiFile != null && !ArrayUtil.contains(psiFile, sourceElements)) {
          relatedFilesToMove.add(psiFile);
        }
      }
    }

    if (!relatedFilesToMove.isEmpty()) {
      final String message = relatedFilesToMove.size() == 1
                             ? RefactoringBundle.message("ask.to.move.related.file", relatedFilesToMove.iterator().next().getName())
                             : RefactoringBundle.message("ask.to.move.related.files",
                                                         StringUtil.join(relatedFilesToMove, PsiFile::getName, ", "));
      final int ok = ApplicationManager.getApplication().isUnitTestMode()
                     ? Messages.YES
                     : Messages.showYesNoDialog(project, message, RefactoringBundle.message("move.title"), Messages.getQuestionIcon());
      if (ok == Messages.YES) {
        final PsiElement[] result = Arrays.copyOf(sourceElements, sourceElements.length + relatedFilesToMove.size());
        ArrayUtil.copy(relatedFilesToMove, result, sourceElements.length);

        return result;
      }
    }

    return sourceElements;
  }
}
