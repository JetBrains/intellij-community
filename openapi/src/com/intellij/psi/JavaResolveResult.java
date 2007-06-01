/*
 * Copyright 2000-2005 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

/**
 * JavaResolveResult holds additional information that is obtained
 * when Java references are being resolved
 *
 * @author ik, dsl
 * @see com.intellij.psi.PsiCall#resolveMethodGenerics()
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