/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection.ui.tree;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.ui.InspectionResultsViewComparator;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.util.*;

/**
 * @author Dmitry Batkovich
 */
public class InspectionTreeBuilder extends AbstractTreeBuilder {
  private final GlobalInspectionContextImpl myContext;

  public InspectionTreeBuilder(Project project,
                               GlobalInspectionContextImpl context) {
    myContext = context;
    final InspectionTree tree = new InspectionTree(this);
    init(tree,
         (DefaultTreeModel)tree.getModel(),
         new InspectionTreeStructure(project),
         (n1, n2) -> InspectionResultsViewComparator.getInstance().compare((InspectionTreeNode)n1, (InspectionTreeNode)n2),
         AbstractTreeBuilder.DEFAULT_UPDATE_INACTIVE);
  }

  public Collection<InspectionTreeNode> getSelectedItems() {
    return getSelectedElements(InspectionTreeNode.class);
  }

  @Nullable
  public InspectionToolWrapper getSelectedToolWrapper() {
    final TreePath[] paths = getTree().getSelectionPaths();
    if (paths == null) return null;
    InspectionToolWrapper toolWrapper = null;
    for (TreePath path : paths) {
      Object[] nodes = path.getPath();
      for (int j = nodes.length - 1; j >= 0; j--) {
        DefaultMutableTreeNode nodeWrapper = (DefaultMutableTreeNode)nodes[j];
        Object userObject = nodeWrapper.getUserObject();
        if (!(userObject instanceof InspectionTreeNode)) continue;
        InspectionTreeNode node = (InspectionTreeNode)userObject;
        if (node instanceof InspectionGroupNode) {
          return null;
        }
        if (node instanceof InspectionNode) {
          InspectionToolWrapper wrapper = ((InspectionNode)node).getToolWrapper();
          if (toolWrapper == null) {
            toolWrapper = wrapper;
          }
          else if (toolWrapper != wrapper) {
            return null;
          }
          break;
        }
      }
    }
    return toolWrapper;
  }

  public RefEntity getCommonSelectedElement() {
    final Object node = getCommonSelectedNode();
    return node instanceof RefElementNode ? ((RefElementNode)node).getRefElement() : null;
  }

  @Nullable
  private Object getCommonSelectedNode() {
    final TreePath[] paths = getTree().getSelectionPaths();
    if (paths == null) return null;
    final Object[][] resolvedPaths = new Object[paths.length][];
    for (int i = 0; i < paths.length; i++) {
      TreePath path = paths[i];
      resolvedPaths[i] = path.getPath();
    }

    Object currentCommonNode = null;
    for (int i = 0; i < resolvedPaths[0].length; i++) {
      final Object currentNode = resolvedPaths[0][i];
      for (int j = 1; j < resolvedPaths.length; j++) {
        final Object o = resolvedPaths[j][i];
        if (!o.equals(currentNode)) {
          return currentCommonNode;
        }
      }
      currentCommonNode = currentNode;
    }
    return currentCommonNode;
  }

  public int getSelectedProblemCount() {
    final TreePath[] paths = getTree().getSelectionPaths();
    if (paths == null || paths.length == 0) return 0;
    Set<DefaultMutableTreeNode> result = new HashSet<>();
    MultiMap<DefaultMutableTreeNode, DefaultMutableTreeNode> rootDependencies = new MultiMap<>();
    for (TreePath path : paths) {
      final DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      final Collection<DefaultMutableTreeNode> visitedChildren = rootDependencies.get(node);
      for (DefaultMutableTreeNode child : visitedChildren) {
        result.remove(child);
      }

      boolean needToAdd = true;
      for (int i = 0; i < path.getPathCount() - 1; i++) {
        final DefaultMutableTreeNode parent = (DefaultMutableTreeNode)path.getPathComponent(i);
        rootDependencies.putValue(parent, node);
        if (result.contains(parent)) {
          needToAdd = false;
          break;
        }
      }

      if (needToAdd) {
        result.add(node);
      }
    }

    int count = 0;
    for (DefaultMutableTreeNode node : result) {
      Object userObject = node.getUserObject();
      if (userObject instanceof InspectionTreeNode) {
        count += ((InspectionTreeNode)userObject).getProblemCount();
      }
    }
    return count;
  }

  public CommonProblemDescriptor[] getSelectedDescriptors() {
    Collection<InspectionTreeNode> items = getSelectedItems();
    if (items.isEmpty()) return CommonProblemDescriptor.EMPTY_ARRAY;
    final LinkedHashSet<CommonProblemDescriptor> descriptors = new LinkedHashSet<CommonProblemDescriptor>();
    getSelectedItems().forEach(n -> traverseDescriptors(n, descriptors));
    return descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
  }

  private static void traverseDescriptors(InspectionTreeNode node, LinkedHashSet<CommonProblemDescriptor> descriptors) {
    if (node instanceof ProblemDescriptionNode) {
      if (node.isValid() && !node.isResolved()) {
        final CommonProblemDescriptor descriptor = ((ProblemDescriptionNode)node).getDescriptor();
        if (descriptor != null) {
          descriptors.add(descriptor);
        }
      }
    }
    Collection<InspectionTreeNode> children = node.getChildren();
    children.forEach(n -> traverseDescriptors(n, descriptors));
  }

  public void removeAllNodes() {
    ((InspectionRootNode)getTreeStructure().getRootElement()).removeAllChildren();
  }

  public RefEntity[] getSelectedRefElements() {
    List<RefEntity> result = new ArrayList<RefEntity>();
    for (InspectionTreeNode node : getSelectedItems()) {
      addElementsInNode(node, result);
    }
    return result.toArray(new RefEntity[result.size()]);
  }

  private static void addElementsInNode(InspectionTreeNode node, List<RefEntity> out) {
    if (!node.isValid()) return;
    if (node instanceof RefElementNode) {
      final RefEntity element = ((RefElementNode)node).getRefElement();
      if (!out.contains(element)) {
        out.add(0, element);
      }
    }
    if (node instanceof ProblemDescriptionNode) {
      final RefEntity element = ((ProblemDescriptionNode)node).getRefElement();
      if (!out.contains(element)) {
        out.add(0, element);
      }
    }
    Collection<InspectionTreeNode> children = node.getChildren();
    children.forEach(child -> addElementsInNode(child, out));
  }

  public GlobalInspectionContextImpl getContext() {
    return myContext;
  }
}
