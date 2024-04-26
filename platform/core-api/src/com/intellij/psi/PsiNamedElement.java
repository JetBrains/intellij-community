// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents a declaration which introduces a named entity and can be renamed (for example, a class or a method).
 * References should not implement this interface since they don't usually introduce a new entity.
 *
 * @see com.intellij.model.psi.PsiSymbolDeclaration
 * @see PsiNameIdentifierOwner
 */
public interface PsiNamedElement extends PsiElement {
  /**
   * The empty array of PSI named elements which can be reused to avoid unnecessary allocations.
   */
  PsiNamedElement[] EMPTY_ARRAY = new PsiNamedElement[0];

  /**
   * Returns the name of the element.
   *
   * @return the element name.
   */
  @Nullable
  @NlsSafe
  String getName();

  /**
   * Renames the element.
   *
   * @param name the new element name.
   * @return the element corresponding to this element after the renaming (either {@code this}
   * or a different element if the renaming caused the element to be replaced).
   * @throws IncorrectOperationException if the modification is not supported or not possible for some reason.
   */
  PsiElement setName(@NlsSafe @NotNull String name) throws IncorrectOperationException;
}
