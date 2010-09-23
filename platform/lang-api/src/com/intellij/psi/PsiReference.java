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

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.codeInsight.lookup.LookupElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reference to a PSI element. For example, the variable name used in an expression.
 * The "Go to Declaration" action can be used to go from a reference to the element it references.
 * Generally returned from {@link PsiElement#getReferences()} and {@link com.intellij.psi.PsiReferenceService#getReferences},
 * but may be contributed to some elements by third party plugins via {@link com.intellij.psi.PsiReferenceContributor}
 *
 * @see PsiElement#getReference()
 * @see PsiElement#getReferences() 
 * @see com.intellij.psi.PsiReferenceService#getReferences
 * @see com.intellij.psi.PsiReferenceBase
 * @see com.intellij.psi.PsiReferenceContributor
 */

public interface PsiReference {
  /**
   * The empty array of PSI references which can be reused to avoid unnecessary allocations.
   */
  PsiReference[] EMPTY_ARRAY = new PsiReference[0];

  /**
   * Returns the underlying (referencing) element of the reference.
   *
   * @return the underlying element of the reference.
   */
  PsiElement getElement();

  /**
   * Returns the part of the underlying element which serves as a reference, or the complete
   * text range of the element if the entire element is a reference.
   *
   * @return Relative range in element
   */
  TextRange getRangeInElement();

  /**
   * Returns the element which is the target of the reference.
   *
   * @return the target element, or null if it was not possible to resolve the reference to a valid target.
   */
  @Nullable PsiElement resolve();

  /**
   * Returns the name of the reference target element which does not depend on import statements
   * and other context (for example, the full-qualified name of the class if the reference targets
   * a Java class).
   *
   * @return the canonical text of the reference.
   */
  @NotNull
  String getCanonicalText();

  /**
   * Called when the reference target element has been renamed, in order to change the reference
   * text according to the new name.
   *
   * @param newElementName the new name of the target element.
   * @return the new underlying element of the reference.
   * @throws IncorrectOperationException if the rename cannot be handled for some reason.
   */
  PsiElement handleElementRename(String newElementName) throws IncorrectOperationException;

  /**
   * Changes the reference so that it starts to point to the specified element. This is called,
   * for example, by the "Create Class from New" quickfix, to bind the (invalid) reference on
   * which the quickfix was called to the newly created class.
   *
   * @param element the element which should become the target of the reference.
   * @return the new underlying element of the reference.
   * @throws IncorrectOperationException if the rebind cannot be handled for some reason.
   */
  PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException;

  /**
   * Checks if the reference targets the specified element.
   *
   * @param element the element to check target for.
   * @return true if the reference targets that element, false otherwise.
   */
  boolean isReferenceTo(PsiElement element);

  /**
   * Returns the array of String, {@link PsiElement} and/or {@link LookupElement}
   * instances representing all identifiers that are visible at the location of the reference. The contents
   * of the returned array is used to build the lookup list for basic code completion. (The list
   * of visible identifiers may not be filtered by the completion prefix string - the
   * filtering is performed later by IDEA core.)
   *
   * @return the array of available identifiers.
   */
  @NotNull 
  Object[] getVariants();

  /**
   * Returns false if the underlying element is guaranteed to be a reference, or true
   * if the underlying element is a possible reference which should not be reported as
   * an error if it fails to resolve. For example, a text in an XML file which looks
   * like a full-qualified Java class name is a soft reference.
   *
   * @return true if the reference is soft, false otherwise.
   */
  boolean isSoft();
}