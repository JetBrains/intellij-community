/**
 * created at Sep 11, 2001
 * @author Jeka
 */
package com.intellij.refactoring.move;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

public class MoveMemberViewDescriptor implements UsageViewDescriptor {
  private final PsiElement[] myElementsToMove;

  public MoveMemberViewDescriptor(PsiElement[] elementsToMove) {
    myElementsToMove = elementsToMove;
  }

  @NotNull
  public PsiElement[] getElements() {
    return myElementsToMove;
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.members.elements.header");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.to.be.changed", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
