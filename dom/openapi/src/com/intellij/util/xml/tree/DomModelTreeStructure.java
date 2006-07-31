package com.intellij.util.xml.tree;

import com.intellij.ui.treeStructure.SimpleTreeStructure;
import com.intellij.util.xml.DomElement;

public class DomModelTreeStructure extends SimpleTreeStructure {
  private final DomElement myDomElement;
  private AbstractDomElementNode myRootNode;

  public DomModelTreeStructure(DomElement root) {
    myDomElement = root;
  }

  protected AbstractDomElementNode createRoot(DomElement rootElement) {
    return new BaseDomElementNode(myDomElement){
      protected boolean highlightIfChildrenHasProblems() {
        return false;
      }
    };
  }

  public AbstractDomElementNode getRootElement() {
    if (myRootNode == null) {
      myRootNode = createRoot(myDomElement);
    }
    return myRootNode;
  }


  public DomElement getRootDomElement() {
    return myDomElement;
  }
}