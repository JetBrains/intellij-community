package com.intellij.psi;

/**
 * @author ven
 */
public interface PsiJavaCodeReferenceCodeFragment extends PsiCodeFragment {
  PsiJavaCodeReferenceElement getReferenceElement();

  /**
   * if true then classes as well as packages are accepted as reference target,
   * otherwise only packages are
   */
  boolean isClassesAccepted ();
}
