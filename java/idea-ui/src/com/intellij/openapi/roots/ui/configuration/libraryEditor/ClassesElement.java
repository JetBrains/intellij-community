package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;

class ClassesElement extends LibraryTableTreeContentElement {
  private final LibraryElement myParent;

  public ClassesElement(LibraryElement parent) {
    myParent = parent;
  }

  public LibraryElement getParent() {
    return myParent;
  }

  public OrderRootType getOrderRootType() {
    return OrderRootType.CLASSES;
  }

  public NodeDescriptor createDescriptor(final NodeDescriptor parentDescriptor, final LibraryTableEditor parentEditor) {
    return new ClassesElementDescriptor(parentDescriptor, this);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ClassesElement)) return false;

    final ClassesElement classesElement = (ClassesElement)o;

    if (!myParent.equals(classesElement.myParent)) return false;

    return true;
  }

  public int hashCode() {
    return myParent.hashCode();
  }
}
