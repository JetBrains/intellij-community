
package com.intellij.refactoring.turnRefsToSuper;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.*;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.refactoring.RefactoringBundle;
import org.jetbrains.annotations.NotNull;

class RefsToSuperViewDescriptor implements UsageViewDescriptor{
  private final PsiClass myClass;
  private final PsiClass mySuper;

  public RefsToSuperViewDescriptor(
    PsiClass aClass,
    PsiClass anInterface
  ) {
    myClass = aClass;
    mySuper = anInterface;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myClass, mySuper};
  }

  public String getProcessedElementsHeader() {
    return null;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    StringBuilder buffer = new StringBuilder();
    buffer.append(RefactoringBundle.message("references.to.0.to.be.replaced.with.references.to.1",
                                            myClass.getName(), mySuper.getName()));
    buffer.append(" ");
    buffer.append(UsageViewBundle.getReferencesString(usagesCount, filesCount));
    return buffer.toString();
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }

}
