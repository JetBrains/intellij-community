package com.intellij.refactoring.move.moveFilesOrDirectories;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

class MoveFilesOrDirectoriesViewDescriptor implements UsageViewDescriptor {
  private PsiElement[] myElementsToMove;
  private String myProcessedElementsHeader;
  private String myCodeReferencesText;

  public MoveFilesOrDirectoriesViewDescriptor(PsiElement[] elementsToMove, PsiDirectory newParent) {
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

  @NotNull
  public PsiElement[] getElements() {
    return myElementsToMove;
  }

  public String getProcessedElementsHeader() {
    return myProcessedElementsHeader;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return myCodeReferencesText + UsageViewBundle.getReferencesString(usagesCount, filesCount);
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("comments.elements.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }

}
