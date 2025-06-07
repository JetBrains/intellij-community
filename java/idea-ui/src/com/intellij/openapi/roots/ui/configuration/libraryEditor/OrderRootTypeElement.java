// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.roots.OrderRootType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class OrderRootTypeElement extends LibraryTableTreeContentElement<OrderRootTypeElement> {
  private final OrderRootType myRootType;

  public OrderRootTypeElement(NodeDescriptor rootElementDescriptor, @NotNull OrderRootType rootType, final String nodeText, final Icon icon) {
    super(rootElementDescriptor);
    myRootType = rootType;
    setIcon(icon);
    myName = nodeText;
  }

  public @NotNull OrderRootType getOrderRootType() {
    return myRootType;
  }

  @Override
  public int hashCode() {
    return myRootType.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof OrderRootTypeElement && ((OrderRootTypeElement)obj).getOrderRootType().equals(myRootType);
  }
}
