package com.intellij.psi.impl.file.impl;

import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;

/**
 *  @author dsl
 */
class EventsTestListener implements PsiTreeChangeListener {
  StringBuffer myBuffer = new StringBuffer();

  public String getEventsString() {
    return myBuffer.toString();
  }

  @Override
  public void beforeChildAddition(PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildAddition\n");
  }

  @Override
  public void beforeChildRemoval(PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildRemoval\n");
  }

  @Override
  public void beforeChildReplacement(PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildReplacement\n");
  }

  @Override
  public void beforeChildMovement(PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildMovement\n");
  }

  @Override
  public void beforeChildrenChange(PsiTreeChangeEvent event) {
    myBuffer.append("beforeChildrenChange\n");
  }

  @Override
  public void beforePropertyChange(PsiTreeChangeEvent event) {
    myBuffer.append("beforePropertyChange\n");
  }

  @Override
  public void childAdded(PsiTreeChangeEvent event) {
    myBuffer.append("childAdded\n");
  }

  @Override
  public void childRemoved(PsiTreeChangeEvent event) {
    myBuffer.append("childRemoved\n");
  }

  @Override
  public void childReplaced(PsiTreeChangeEvent event) {
    myBuffer.append("childReplaced\n");
  }

  @Override
  public void childrenChanged(PsiTreeChangeEvent event) {
    myBuffer.append("childrenChanged\n");
  }

  @Override
  public void childMoved(PsiTreeChangeEvent event) {
    myBuffer.append("childMoved\n");
  }

  @Override
  public void propertyChanged(PsiTreeChangeEvent event) {
    myBuffer.append("propertyChanged\n");
  }
}
