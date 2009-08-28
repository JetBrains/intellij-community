package com.intellij.ide.structureView.impl;



/**
 * @author Yura Cangea
 */
public final class StructureViewState {
  private Object[] myExpandedElements;
  private Object[] mySelectedElements;

  public Object[] getExpandedElements() {
    return myExpandedElements;
  }

  public void setExpandedElements(Object[] expandedElements) {
    myExpandedElements = expandedElements;
  }

  public Object[] getSelectedElements() {
    return mySelectedElements;
  }

  public void setSelectedElements(Object[] selectedElements) {
    mySelectedElements = selectedElements;
  }
}
