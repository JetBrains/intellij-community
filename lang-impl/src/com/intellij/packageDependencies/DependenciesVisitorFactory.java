/*
 * User: anna
 * Date: 21-Jan-2008
 */
package com.intellij.packageDependencies;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReference;

public class DependenciesVisitorFactory {

  public static DependenciesVisitorFactory getInstance() {
    return ServiceManager.getService(DependenciesVisitorFactory.class);
  }

  public PsiElementVisitor createVisitor(final DependenciesBuilder.DependencyProcessor processor) {
    return new PsiRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        super.visitElement(element);
        PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          PsiElement resolved = ref.resolve();
          if (resolved != null) {
            processor.process(ref.getElement(), resolved);
          }
        }
      }
    };
  }
}