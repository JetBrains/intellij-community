// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * A node in the reference graph corresponding to a PSI element.
 *
 * @author anna
 */
public interface RefElement extends RefEntity {
  /**
   * Returns the reference graph node for the module to which the element belongs.
   *
   * @return the node for the module, or null if the element is not valid or the module
   * was not found.
   */
  @Nullable RefModule getModule();

  /**
   * Returns the PSI element corresponding to the node.
   *
   * @return the PSI element.
   */
  default PsiElement getPsiElement() {
    throw new UnsupportedOperationException();
  }

  /**
   * @deprecated use {@link #getPsiElement()}
   */
  @Deprecated(forRemoval = true)
  default PsiElement getElement() {
    return getPsiElement();
  }

  SmartPsiElementPointer<?> getPointer();

  /**
   * Checks if a chain of references exists from one of the entry points to this element.
   *
   * @return true if the element is reachable from one of the entry points, false otherwise.
   */
  boolean isReachable();

  /**
   * Checks if this element is referenced by any other elements.
   *
   * @return true if the element is referenced, false otherwise.
   */
  boolean isReferenced();

  /**
   * Checks if this element has been initialized.
   *
   * @return true if the element has been initialized, false otherwise.
   */
  default boolean isInitialized() {
    return true;
  }

  /**
   * Blocks until this element has been initialized.
   */
  default void waitForInitialized() {}

  /**
   * Returns the collection of references from this element to other elements.
   *
   * @return the collection of outgoing references.
   */
  @NotNull Collection<RefElement> getOutReferences();

  /**
   * Returns the collection of references from other elements to this element.
   *
   * @return the collection of incoming references.
   */
  @NotNull Collection<RefElement> getInReferences();

  /**
   * Checks if the element is an entry point for reachability analysis.
   *
   * @return true if the element is an entry point, false otherwise.
   */
  boolean isEntry();

  /**
   * Checks if the element has been specifically marked by the user as an entry point
   * for reachability analysis.
   *
   * @return true if the element has been marked as an entry point, false otherwise.
   */
  boolean isPermanentEntry();

  @NotNull
  RefElement getContainingEntry();
}
