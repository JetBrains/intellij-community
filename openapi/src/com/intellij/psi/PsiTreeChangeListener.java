/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import java.util.EventListener;

/**
 *
 */
public interface PsiTreeChangeListener extends EventListener {
  /**
   * Invoked just before adding a child from the tree.
   * Parent element is returned by <code>event.getParent()</code>.
   */
  void beforeChildAddition(PsiTreeChangeEvent event);

  /**
   * Invoked just before removal of a child from the tree.
   * Child to be removed is returned by <code>event.getChild()</code>.
   * Parent element is returned by <code>event.getParent()</code>.
   */
  void beforeChildRemoval(PsiTreeChangeEvent event);

  /**
   * Invoked just before replacement of a child in the tree by another element.
   * Child to be replaced is returned by <code>event.getOldChild()</code>.
   * Parent element is returned by <code>event.getParent()</code>.
   */
  void beforeChildReplacement(PsiTreeChangeEvent event);

  /**
   * Invoked just before movement of a child in the tree by changing its parent or by changing its position in the same parent.
   * Child to be moved is returned by <code>event.getChild()</code>.
   * The old parent before is returned by <code>event.getOldParent()</code>.
   * The new parent before is returned by <code>event.getNewParent()</code>.
   */
  void beforeChildMovement(PsiTreeChangeEvent event);

  void beforeChildrenChange(PsiTreeChangeEvent event);

  /**
   * Invoked just before changing of some property of an element.
   * Element, whose property is to be changed is returned by <code>event.getElement()</code>.
   * The property name is returned by <code>event.getPropertyName()</code>.
   * The old property value is returned by <code>event.getOldValue()</code>.
   */
  void beforePropertyChange(PsiTreeChangeEvent event);

  /**
   * Invoked just after adding of a new child to the tree.
   * The added child is returned by <code>event.getChild()</code>.
   * Parent element is returned by <code>event.getParent()</code>.
   */
  void childAdded(PsiTreeChangeEvent event);

  /**
   * Invoked just after removal of a child from the tree.
   * The removed child is returned by <code>event.getChild()</code>. Note that only <code>equals()</code>, <code>hashCode()</code>, <code>isValid()</code> methods can be safely invoked for this element, because it's not valid anymore.
   * Parent element is returned by <code>event.getParent()</code>.
   */
  void childRemoved(PsiTreeChangeEvent event);

  /**
   * Invoked just after replacement of a child in the tree by another element.
   * The replaced child is returned by <code>event.getOldChild()</code>. Note that only <code>equals()</code>, <code>hashCode()</code>, <code>isValid()</code> methods can be safely invoked for this element, because it's not valid anymore.
   * The new child is returned by <code>event.getNewChild()</code>.
   * Parent element is returned by <code>event.getParent()</code>.
   */
  void childReplaced(PsiTreeChangeEvent event);

  void childrenChanged(PsiTreeChangeEvent event);

  /**
   * Invoked just after movement of a child in the tree by changing its parent or by changing its position in the same parent.
   * The moved child is returned by <code>event.getChild()</code>.
   * The old parent is returned by <code>event.getOldParent()</code>. Note that only <code>equals()</code>, <code>hashCode()</code>, <code>isValid()</code> methods can be safely invoked for this element, because it's not valid anymore.
   * The new parent is returned by <code>event.getNewParent()</code>.
   */
  void childMoved(PsiTreeChangeEvent event);

  /**
   * Invoked just after changing of some property of an element.
   * Element, whose property has changed is returned by <code>event.getElement()</code>.
   * The property name is returned by <code>event.getPropertyName()</code>.
   * The old property value is returned by <code>event.getOldValue()</code>.
   * The new property value is returned by <code>event.getNewValue()</code>.
   */
  void propertyChanged(PsiTreeChangeEvent event);
}
