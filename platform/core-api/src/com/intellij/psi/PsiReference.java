// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.ArrayFactory;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reference to a PSI element. For example, the variable name used in an expression.
 * The "Go to Declaration" action can be used to go from a reference to the element it references.
 * <p>
 * Generally returned from {@link PsiElement#getReferences()} and {@link PsiReferenceService#getReferences},
 * but may be contributed to some elements by third party plugins via {@link PsiReferenceContributor}.
 *
 * @see PsiPolyVariantReference
 * @see PsiElement#getReference()
 * @see PsiElement#getReferences()
 * @see PsiReferenceService#getReferences(PsiElement, PsiReferenceService.Hints)
 * @see PsiReferenceBase
 * @see PsiReferenceContributor
 */
public interface PsiReference {

  PsiReference[] EMPTY_ARRAY = new PsiReference[0];

  ArrayFactory<PsiReference> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new PsiReference[count];

  /**
   * Returns the underlying (referencing) element of the reference.
   *
   * @return the underlying element of the reference.
   */
  @NotNull
  PsiElement getElement();

  /**
   * Returns the part of the underlying element which serves as a reference, or the complete
   * text range of the element if the entire element is a reference.
   * <p/>
   * Sample: PsiElement representing a fully qualified name with multiple dedicated PsiReferences, each bound
   * to the range it resolves to (skipping the '.' separator).
   * <pre>
   * PsiElement text: qualified.LongName
   * PsiReferences:   [Ref1---]X[Ref2--]
   * </pre>
   * where {@code Ref1} would resolve to a "namespace" and {@code Ref2} to an "element".
   *
   * @return Relative range in element
   */
  @NotNull
  TextRange getRangeInElement();

  /**
   * @return range in the {@link PsiElement#getContainingFile containing file} of the {@link #getElement element}
   * which is considered a reference
   * @see #getRangeInElement
   */
  @NotNull
  default TextRange getAbsoluteRange() {
    return getRangeInElement().shiftRight(getElement().getTextRange().getStartOffset());
  }

  /**
   * Returns the element which is the target of the reference.
   *
   * @return the target element, or {@code null} if it was not possible to resolve the reference to a valid target.
   * @see PsiPolyVariantReference#multiResolve(boolean)
   */
  @Nullable
  PsiElement resolve();

  /**
   * Returns the name of the reference target element which does not depend on import statements
   * and other context (for example, the full-qualified name of the class if the reference targets
   * a Java class).
   *
   * @return the canonical text of the reference.
   */
  @NotNull @NlsSafe String getCanonicalText();

  /**
   * Called when the reference target element has been renamed, in order to change the reference
   * text according to the new name.
   *
   * @param newElementName the new name of the target element.
   * @return the new underlying element of the reference.
   * @throws IncorrectOperationException if the rename cannot be handled for some reason.
   */
  PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException;

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
   * @return {@code true} if the reference targets that element, {@code false} otherwise.
   */
  boolean isReferenceTo(@NotNull PsiElement element);

  /**
   * Returns the array of String, {@link PsiElement} and/or {@link com.intellij.codeInsight.lookup.LookupElement}
   * instances representing all identifiers that are visible at the location of the reference. The contents
   * of the returned array is used to build the lookup list for basic code completion. (The list
   * of visible identifiers may not be filtered by the completion prefix string - the
   * filtering is performed later by the IDE.)
   * <p>
   * This method is default since 2018.3.
   *
   * @return the array of available identifiers.
   */
  default Object @NotNull [] getVariants() {
    return ArrayUtilRt.EMPTY_OBJECT_ARRAY;
  }

  /**
   * Returns {@code false} if the underlying element is guaranteed to be a reference, or {@code true}
   * if the underlying element is a possible reference which should not be reported as
   * an error if it fails to resolve. For example, a text in an XML file which looks
   * like a full-qualified Java class name is a soft reference.
   *
   * @return {@code true} if the reference is soft, {@code false} otherwise.
   */
  boolean isSoft();
}
