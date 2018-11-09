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

import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Listener for receiving notifications about all changes in the PSI tree of a project.<p></p>
 *
 * Try to avoid processing PSI events at all cost! See {@link PsiTreeChangeEvent} documentation for more details.
 *
 * @see PsiManager#addPsiTreeChangeListener(PsiTreeChangeListener)
 * @see PsiManager#removePsiTreeChangeListener(PsiTreeChangeListener)
 */
public interface PsiTreeChangeListener extends EventListener {
  /**
   * Invoked just before adding a child to the tree.<br>
   * Parent element is returned by {@code event.getParent()}.<br>
   * Added child is returned by {@code event.getChild}.
   *
   * @param event the event object describing the change.
   */
  void beforeChildAddition(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just before removal of a child from the tree.<br>
   * Child to be removed is returned by {@code event.getChild()}.<br>
   * Parent element is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void beforeChildRemoval(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just before replacement of a child in the tree by another element.<br>
   * Child to be replaced is returned by {@code event.getOldChild()}.<br>
   * Parent element is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void beforeChildReplacement(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just before movement of a child in the tree by changing its parent or by changing its position in the same parent.<br>
   * Child to be moved is returned by {@code event.getChild()}.<br>
   * The old parent is returned by {@code event.getOldParent()}.<br>
   * The new parent is returned by {@code event.getNewParent()}.
   *
   * @param event the event object describing the change.
   */
  void beforeChildMovement(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked before a mass change of children of the specified node.<br>
   * The parent the nodes of which are changing is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void beforeChildrenChange(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just before changing of some property of an element.<br>
   * Element, whose property is to be changed is returned by {@code event.getElement()}.<br>
   * The property name is returned by {@code event.getPropertyName()}.<br>
   * The old property value is returned by {@code event.getOldValue()}.
   *
   * @param event the event object describing the change.
   */
  void beforePropertyChange(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just after adding of a new child to the tree.<br>
   * The added child is returned by {@code event.getChild()}.<br>
   * Parent element is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void childAdded(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just after removal of a child from the tree.<br>
   * The removed child is returned by {@code event.getChild()}. Note that
   * only {@code equals()}, {@code hashCode()}, {@code isValid()} methods
   * can be safely invoked for this element, because it's not valid anymore.<br>
   * Parent element is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void childRemoved(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just after replacement of a child in the tree by another element.<br>
   * The replaced child is returned by {@code event.getOldChild()}. Note that
   * only {@code equals()}, {@code hashCode()}, {@code isValid()} methods
   * can be safely invoked for this element, because it's not valid anymore.<br>
   * The new child is returned by {@code event.getNewChild()}.<br>
   * Parent element is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void childReplaced(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked after a mass change of children of the specified node.<br>
   * The parent the nodes of which have changed is returned by {@code event.getParent()}.
   *
   * @param event the event object describing the change.
   */
  void childrenChanged(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just after movement of a child in the tree by changing its parent or by changing its position in the same parent.<br>
   * The moved child is returned by {@code event.getChild()}.<br>
   * The old parent is returned by {@code event.getOldParent()}.<br>
   * The new parent is returned by {@code event.getNewParent()}.
   *
   * @param event the event object describing the change.
   */
  void childMoved(@NotNull PsiTreeChangeEvent event);

  /**
   * Invoked just after changing of some property of an element.<br>
   * Element, whose property has changed is returned by {@code event.getElement()}.<br>
   * The property name is returned by {@code event.getPropertyName()}.<br>
   * The old property value is returned by {@code event.getOldValue()}.<br>
   * The new property value is returned by {@code event.getNewValue()}.
   *
   * @param event the event object describing the change.
   */
  void propertyChanged(@NotNull PsiTreeChangeEvent event);
}
