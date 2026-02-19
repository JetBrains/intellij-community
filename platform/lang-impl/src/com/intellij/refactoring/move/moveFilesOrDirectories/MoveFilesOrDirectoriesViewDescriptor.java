// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

final class MoveFilesOrDirectoriesViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElementsToMove;
  private @NlsContexts.ListItem String myProcessedElementsHeader;
  private final @Nls String myCodeReferencesText;

  MoveFilesOrDirectoriesViewDescriptor(PsiElement[] elementsToMove, PsiDirectory newParent) {
    myElementsToMove = elementsToMove;
    if (elementsToMove.length == 1) {
      myProcessedElementsHeader = StringUtil.capitalize(RefactoringBundle.message("move.single.element.elements.header",
                                                                                  UsageViewUtil.getType(elementsToMove[0]),
                                                                                  newParent.getVirtualFile().getPresentableUrl()));
      myCodeReferencesText = RefactoringBundle.message("references.in.code.to.0.1",
                                                       UsageViewUtil.getType(elementsToMove[0]), UsageViewUtil.getLongName(elementsToMove[0]));
    }
    else {
      if (elementsToMove[0] instanceof PsiFile) {
        myProcessedElementsHeader =
          StringUtil.capitalize(RefactoringBundle.message("move.files.elements.header", newParent.getVirtualFile().getPresentableUrl()));
      }
      else if (elementsToMove[0] instanceof PsiDirectory){
        myProcessedElementsHeader = StringUtil
          .capitalize(RefactoringBundle.message("move.directories.elements.header", newParent.getVirtualFile().getPresentableUrl()));
      }
      myCodeReferencesText = RefactoringBundle.message("references.found.in.code");
    }
  }

  @Override
  public PsiElement @NotNull [] getElements() {
    return myElementsToMove;
  }

  @Override
  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  @Override
  public @NotNull String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  @Override
  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
