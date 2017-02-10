/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.roots.ui.configuration.artifacts;

import com.intellij.openapi.roots.ui.configuration.artifacts.nodes.PackagingElementNode;
import com.intellij.packaging.elements.PackagingElement;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
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
      if (node instanceof PackagingElementNode) {
        final PackagingElementNode<?> elementNode = (PackagingElementNode<?>)node;
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

  @Nullable
  public CompositePackagingElement<?> getCommonParentElement() {
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

  @Nullable
  public PackagingElement<?> getElementIfSingle() {
    return mySelectedElements.size() == 1 ? mySelectedElements.get(0) : null;
  }

  @Nullable
  public PackagingElementNode<?> getNodeIfSingle() {
    return mySelectedNodes.size() == 1 ? mySelectedNodes.get(0) : null;
  }
}
