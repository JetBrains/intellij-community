package com.intellij.refactoring.replaceConstructorWithFactory;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
class ReplaceConstructorWithFactoryViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiMethod myConstructor;
  private final PsiClass myClass;

  public ReplaceConstructorWithFactoryViewDescriptor(
    PsiMethod constructor) {
    super();
    myConstructor = constructor;
    myClass = null;
  }

  public ReplaceConstructorWithFactoryViewDescriptor(PsiClass aClass) {
    super();
    myClass = aClass;
    myConstructor = null;
  }

  @NotNull
  public PsiElement[] getElements() {
    if (myConstructor != null) {
      return new PsiElement[] {myConstructor};
    } else {
      return new PsiElement[] {myClass};
    }
  }

  public String getProcessedElementsHeader() {
    if (myConstructor != null) {
      return RefactoringBundle.message("replace.constructor.with.factory.method");
    } else {
      return RefactoringBundle.message("replace.default.constructor.with.factory.method");
    }
  }
}
