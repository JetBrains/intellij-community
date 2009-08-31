package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.Nullable;

public abstract class LibraryTableTreeContentElement {
  // empty, just to serve as a base for all tree structure elements

  public abstract LibraryTableTreeContentElement getParent();

  @Nullable
  public abstract OrderRootType getOrderRootType();

  public abstract NodeDescriptor createDescriptor(NodeDescriptor parentDescriptor, final LibraryTableEditor parentEditor);
}
