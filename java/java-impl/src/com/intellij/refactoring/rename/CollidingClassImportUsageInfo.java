package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiImportStatement;

/**
 * @author dsl
 */
public class CollidingClassImportUsageInfo extends ResolvableCollisionUsageInfo {
  private final PsiImportStatement myImportStatement;

  public CollidingClassImportUsageInfo(PsiImportStatement element, PsiElement referencedElement) {
    super(element, referencedElement);
    myImportStatement = element;
  }

  public PsiImportStatement getImportStatement() {
    return myImportStatement;
  }

}
