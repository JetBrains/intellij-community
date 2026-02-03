// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LayoutTreeSelection {
  private final List<PackagingElementNode<?>> mySelectedNodes = new ArrayList<>();
  private final List<PackagingElement<?>> mySelectedElements = new ArrayList<>();
  private final Map<PackagingElement<?>, PackagingElementNode<?>> myElement2Node = new HashMap<>();
  private final Map<PackagingElementNode<?>, TreePath> myNode2Path = new HashMap<>();

  public LayoutTreeSelection(@NotNull LayoutTree tree) {
    final TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return;
    }

    for (TreePath path : paths) {
      final SimpleNode node = tree.getNodeFor(path);
      if (node instanceof PackagingElementNode<?> elementNode) {
        mySelectedNodes.add(elementNode);
        myNode2Path.put(elementNode, path);
        for (PackagingElement<?> element : elementNode.getPackagingElements()) {
          mySelectedElements.add(element);
          myElement2Node.put(element, elementNode);
        }
      }
    }
  }

  public List<PackagingElementNode<?>> getNodes() {
    return mySelectedNodes;
  }

  public List<PackagingElement<?>> getElements() {
    return mySelectedElements;
  }

  public PackagingElementNode<?> getNode(@NotNull PackagingElement<?> element) {
    return myElement2Node.get(element);
  }

  public TreePath getPath(@NotNull PackagingElementNode<?> node) {
    return myNode2Path.get(node);
  }

  public @Nullable CompositePackagingElement<?> getCommonParentElement() {
    CompositePackagingElement<?> commonParent = null;
    for (PackagingElementNode<?> selectedNode : mySelectedNodes) {
      final PackagingElement<?> element = selectedNode.getElementIfSingle();
      if (element == null) return null;

      final CompositePackagingElement<?> parentElement = selectedNode.getParentElement(element);
      if (parentElement == null || commonParent != null && !commonParent.equals(parentElement)) {
        return null;
      }
      commonParent = parentElement;
    }
    return commonParent;
  }

  public @Nullable PackagingElement<?> getElementIfSingle() {
    return mySelectedElements.size() == 1 ? mySelectedElements.get(0) : null;
  }

  public @Nullable PackagingElementNode<?> getNodeIfSingle() {
    return mySelectedNodes.size() == 1 ? mySelectedNodes.get(0) : null;
  }
}
