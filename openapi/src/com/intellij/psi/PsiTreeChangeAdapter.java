/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

/**
 *
 */
public abstract class PsiTreeChangeAdapter implements PsiTreeChangeListener {
  public void beforeChildAddition(PsiTreeChangeEvent event) {
  }

  public void beforeChildRemoval(PsiTreeChangeEvent event) {
  }

  public void beforeChildReplacement(PsiTreeChangeEvent event) {
  }

  public void beforeChildMovement(PsiTreeChangeEvent event) {
  }

  public void beforeChildrenChange(PsiTreeChangeEvent event) {
  }

  public void beforePropertyChange(PsiTreeChangeEvent event) {
  }

  public void childAdded(PsiTreeChangeEvent event) {
  }

  public void childRemoved(PsiTreeChangeEvent event) {
  }

  public void childReplaced(PsiTreeChangeEvent event) {
  }

  public void childMoved(PsiTreeChangeEvent event) {
  }

  public void childrenChanged(PsiTreeChangeEvent event) {
  }

  public void propertyChanged(PsiTreeChangeEvent event) {
  }
}
