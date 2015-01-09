package com.intellij.psi.impl.file.impl;

import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;
import org.jetbrains.annotations.NotNull;

/**
 *  @author dsl
 */
class EventsTestListener implements PsiTreeChangeListener {
  StringBuffer myBuffer = new StringBuffer();

  public String getEventsString() {
    return myBuffer.toString();
  }

  @Override
  public void beforeChildAddition(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildAddition\n");
  }

  @Override
  public void beforeChildRemoval(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildRemoval\n");
  }

  @Override
  public void beforeChildReplacement(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildReplacement\n");
  }

  @Override
  public void beforeChildMovement(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildMovement\n");
  }

  @Override
  public void beforeChildrenChange(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildrenChange\n");
  }

  @Override
  public void beforePropertyChange(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("beforePropertyChange " + event.getPropertyName() + "\n");
  }

  @Override
  public void childAdded(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childAdded\n");
  }

  @Override
  public void childRemoved(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childRemoved\n");
  }

  @Override
  public void childReplaced(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childReplaced\n");
  }

  @Override
  public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childrenChanged\n");
  }

  @Override
  public void childMoved(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("childMoved\n");
  }

  @Override
  public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
    myBuffer.append("propertyChanged " + event.getPropertyName() + "\n");
  }
}
