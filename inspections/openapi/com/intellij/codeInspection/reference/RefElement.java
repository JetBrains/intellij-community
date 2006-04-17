/*
 * Copyright 2000-2006 JetBrains s.r.o.
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
   * Checks if the PSI element corresponding to the node is valid.
   *
   * @return true if the element is valid, false otherwise.
   */
  boolean isValid();

  /**
   * Returns the reference graph manager for the node.
   *
   * @return the reference graph element for the instance.
   */
  RefManager getRefManager();

  /**
   * Returns a user-readable name for the element corresponding to the node.
   *
   * @return the user-readable name.
   */
  String getExternalName();

  /**
   * Returns the PSI element corresponding to the node.
   *
   * @return the PSI element.
   */
  PsiElement getElement();

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
   * Returns the collection of references used in this element.
   * @return the collection of used types
   */
  @NotNull Collection<RefClass> getOutTypeReferences();

  /**
   * Checks if the element is <code>final</code>.
   *
   * @return true if the element is final, false otherwise.
   */
  boolean isFinal();

  /**
   * Checks if the element is <code>static</code>.
   *
   * @return true if the element is static, false otherwise.
   */
  boolean isStatic();

  /**
   * Checks if the element directly references any elements marked as deprecated.
   *
   * @return true if the element references any deprecated elements, false otherwise.
   */
  boolean isUsesDeprecatedApi();

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

  /**
   * Checks if the element is, or belongs to, a synthetic class or method created for a JSP page.
   *
   * @return true if the element is a synthetic JSP element, false otherwise.
   */
  boolean isSyntheticJSP();

  /**
   * Returns the access modifier for the element, as one of the keywords from the
   * {@link com.intellij.psi.PsiModifier} class.
   *
   * @return the modifier, or null if the element does not have any access modifier.
   */
  @Nullable
  String getAccessModifier();
}
