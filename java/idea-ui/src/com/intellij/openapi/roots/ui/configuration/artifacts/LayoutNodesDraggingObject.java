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
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.impl.artifacts.ArtifactUtil;
import com.intellij.ui.treeStructure.SimpleNode;
import org.jetbrains.annotations.NotNull;

import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class LayoutNodesDraggingObject extends PackagingElementDraggingObject {
  private final ArtifactEditorEx myArtifactsEditor;
  private final List<PackagingElementNode<?>> myNodes;

  public LayoutNodesDraggingObject(ArtifactEditorEx artifactsEditor, List<PackagingElementNode<?>> nodes) {
    myArtifactsEditor = artifactsEditor;
    myNodes = nodes;
  }

  @Override
  public List<PackagingElement<?>> createPackagingElements(ArtifactEditorContext context) {
    final List<PackagingElement<?>> result = new ArrayList<>();

    for (PackagingElementNode<?> node : myNodes) {
      final List<? extends PackagingElement<?>> elements = node.getPackagingElements();
      for (PackagingElement<?> element : elements) {
        result.add(ArtifactUtil.copyWithChildren(element, myArtifactsEditor.getContext().getProject()));
      }
    }

    return result;
  }

  @Override
  public boolean checkCanDrop() {
    return myArtifactsEditor.getLayoutTreeComponent().checkCanRemove(myNodes);
  }

  @Override
  public void beforeDrop() {
    myArtifactsEditor.getLayoutTreeComponent().removeNodes(myNodes);
  }

  @Override
  public boolean canDropInto(@NotNull PackagingElementNode node) {
    final LayoutTree tree = myArtifactsEditor.getLayoutTreeComponent().getLayoutTree();
    final TreePath path = tree.getPathFor(node);
    if (path != null) {
      for (PackagingElementNode<?> selectedNode : myNodes) {
        if (pathContains(path, selectedNode, tree)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean pathContains(TreePath path, PackagingElementNode<?> node, LayoutTree tree) {
    while (path != null) {
      final SimpleNode pathNode = tree.getNodeFor(path);
      if (pathNode == node) {
        return true;
      }
      path = path.getParentPath();
    }
    return false;
  }
}
