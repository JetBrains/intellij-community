// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

/**
 * Represents a fragment of Java code the contents of which is a reference element
 * referencing a Java class or package.
 *
 * @see JavaCodeFragmentFactory#createReferenceCodeFragment(String, PsiElement, boolean, boolean)
 * @see JavaCodeFragmentFactory#createReferenceCodeFragmentInPackage(String, String, boolean)
 */
public interface PsiJavaCodeReferenceCodeFragment extends JavaCodeFragment {
  /**
   * Returns the reference contained in the fragment.
   *
   * @return the reference element instance.
   */
  PsiJavaCodeReferenceElement getReferenceElement();

  /**
   * Checks if classes are accepted as the target of the reference.
   *
   * @return if true then classes as well as packages are accepted as reference target,
   * otherwise only packages are.
   */
  boolean isClassesAccepted();
}
