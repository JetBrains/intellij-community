package com.intellij.refactoring.safeDelete;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import com.intellij.usageView.UsageViewBundle;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class SafeDeleteUsageViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiElement[] myElementsToDelete;

  public SafeDeleteUsageViewDescriptor(
    PsiElement[] elementsToDelete
  ) {
    super();
    myElementsToDelete = elementsToDelete;
  }

  @NotNull
  public PsiElement[] getElements() {
    return myElementsToDelete;
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("items.to.be.deleted");
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("references.in.code", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("safe.delete.comment.occurences.header",
                                     UsageViewBundle.getOccurencesString(usagesCount, filesCount));
  }
}
