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
 * @since 6.0
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
  PsiElement getElement();

  SmartPsiElementPointer getPointer();

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
