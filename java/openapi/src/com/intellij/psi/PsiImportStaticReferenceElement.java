/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.util.IncorrectOperationException;

/**
 * Represents a reference to the member imported by a Java <code>import static</code>
 * statement.
 *
 * @author dsl
 */
public interface PsiImportStaticReferenceElement extends PsiJavaCodeReferenceElement {
  /**
   * Returns the reference element specifying the class from which the member is imported.
   *
   * @return the reference element specifying the class.
   */
  PsiJavaCodeReferenceElement getClassReference();

  /**
   * Binds the reference element to the specified class.
   *
   * @param aClass the class to bind the reference element to.
   * @return the element corresponding to this element in the PSI tree after the rebind.
   * @throws IncorrectOperationException if the modification fails for some reason (for example,
   * the containing file is read-only).
   * @see PsiReference#bindToElement(PsiElement) 
   */
  PsiImportStaticStatement bindToTargetClass(PsiClass aClass) throws IncorrectOperationException;
}
