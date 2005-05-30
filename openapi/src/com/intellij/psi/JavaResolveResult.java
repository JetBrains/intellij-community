/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 * JavaResolveResult holds additional information that is obtained
 * when Java references are being resolved
 *  @author ik, dsl
 */
public interface JavaResolveResult extends ResolveResult {
  JavaResolveResult[] EMPTY_ARRAY = new JavaResolveResult[0];

  /**
   * Substitutor providing values of type parameters occuring
   * in {@link #getElement()}.
   * @return
   */
  PsiSubstitutor getSubstitutor();

  boolean isPackagePrefixPackageReference();

  /**
   * Checks whether {@link #getElement()} is accessible from reference.
   * @return
   */
  boolean isAccessible();

  boolean isStaticsScopeCorrect();

  /**
   * @return scope in the reference's file where the reference has been resolved
   *         null for qualified and local references
   */
  PsiElement getCurrentFileResolveScope();


  JavaResolveResult EMPTY = new JavaResolveResult(){
    public PsiElement getElement(){return null;}
    public PsiSubstitutor getSubstitutor(){return PsiSubstitutor.EMPTY;}
    public boolean isValidResult(){return false;}
    public boolean isAccessible(){return false;}
    public boolean isStaticsScopeCorrect(){return false;}
    public PsiElement getCurrentFileResolveScope() { return null; }

    public boolean isPackagePrefixPackageReference() { return false; }
  };
}