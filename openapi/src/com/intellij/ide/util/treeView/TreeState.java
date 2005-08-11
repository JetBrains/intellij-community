/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.*;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.psi.PsiElement;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.List;

public class TreeState implements JDOMExternalizable {
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static final String PATH = "PATH";
  @SuppressWarnings({"HardCodedStringLiteral"})
  private static final String PATH_ELEMENT = "PATH_ELEMENT";

  static class PathElement implements JDOMExternalizable {
    public String myItemId;
    public String myItemType;

    private final int myItemIndex;
    private final Object myUserObject;

    public PathElement(final String itemId, final String itemType, final int itemIndex, Object userObject) {
      myItemId = itemId;
      myItemType = itemType;

      myItemIndex = itemIndex;
      myUserObject = userObject;
    }

    public PathElement() {
      myItemIndex = -1;
      myUserObject = null;
    }

    public boolean matchedWith(NodeDescriptor nodeDescriptor) {
      return Comparing.equal(myItemId, getDescriptorKey(nodeDescriptor)) &&
             Comparing.equal(myItemType, getDescriptorType(nodeDescriptor));
    }

    public boolean matchedWithByIndex(NodeDescriptor nodeDescriptor) {
      return Comparing.equal(myItemIndex, nodeDescriptor.getIndex());
    }

    public boolean matchedWithByObject(NodeDescriptor nodeDescriptor) {
      return myUserObject != null && myUserObject.equals(nodeDescriptor);
    }

    public void readExternal(Element element) throws InvalidDataException {
      DefaultJDOMExternalizer.readExternal(this, element);
    }

    public void writeExternal(Element element) throws WriteExternalException {
      DefaultJDOMExternalizer.writeExternal(this, element);
    }
  }

  private final List<List<PathElement>> myExpandedPaths;
  private final List<List<PathElement>> mySelectedPaths;

  private TreeState(List<List<PathElement>> paths, final List<List<PathElement>> selectedPaths) {
    myExpandedPaths = paths;
    mySelectedPaths = selectedPaths;
  }

  public TreeState() {
    myExpandedPaths = new ArrayList<List<PathElement>>();
    mySelectedPaths = new ArrayList<List<PathElement>>();
  }

  public void readExternal(Element element) throws InvalidDataException {
    myExpandedPaths.clear();
    final List paths = element.getChildren(PATH);
    for (final Object path : paths) {
      Element xmlPathElement = (Element)path;
      myExpandedPaths.add(readPath(xmlPathElement));
    }
  }

  private List<PathElement> readPath(final Element xmlPathElement) throws InvalidDataException {
    final ArrayList<PathElement> result = new ArrayList<PathElement>();
    final List elements = xmlPathElement.getChildren(PATH_ELEMENT);
    for (final Object element : elements) {
      Element xmlPathElementElement = (Element)element;
      final PathElement pathElement = new PathElement();
      pathElement.readExternal(xmlPathElementElement);
      result.add(pathElement);
    }
    return result;
  }

  public static TreeState createOn(JTree tree, final DefaultMutableTreeNode treeNode) {
    return new TreeState(createExpandedPaths(tree, treeNode), createSelectedPaths(tree, treeNode));
  }

  public void writeExternal(Element element) throws WriteExternalException {
    for (List<PathElement> path : myExpandedPaths) {
      final Element pathElement = new Element(PATH);
      writeExternal(pathElement, path);
      element.addContent(pathElement);
    }
  }

  private void writeExternal(final Element pathXmlElement, final List<PathElement> path) throws WriteExternalException {
    for (final PathElement aPath : path) {
      final Element pathXmlElementElement = new Element(PATH_ELEMENT);
      aPath.writeExternal(pathXmlElementElement);
      pathXmlElement.addContent(pathXmlElementElement);
    }
  }

  public static TreeState createOn(JTree tree) {
    return new TreeState(createPaths(tree), new ArrayList<List<PathElement>>());
  }


  private static List<List<PathElement>> createPaths(final JTree tree) {
    final ArrayList<List<PathElement>> result = new ArrayList<List<PathElement>>();
    final java.util.List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(tree);
    for (final TreePath expandedPath : expandedPaths) {
      final List<PathElement> path = createPath(expandedPath);
      if (path != null) {
        result.add(path);
      }
    }
    return result;
  }

  private static List<List<PathElement>> createExpandedPaths(JTree tree, final DefaultMutableTreeNode treeNode) {
    final ArrayList<List<PathElement>> result = new ArrayList<List<PathElement>>();
    final java.util.List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(tree, new TreePath(treeNode.getPath()));
    for (final TreePath expandedPath : expandedPaths) {
      final List<PathElement> path = createPath(expandedPath);
      if (path != null) {
        result.add(path);
      }
    }
    return result;
  }

  private static List<List<PathElement>> createSelectedPaths(JTree tree, final DefaultMutableTreeNode treeNode) {
    final ArrayList<List<PathElement>> result = new ArrayList<List<PathElement>>();
    final java.util.List<TreePath> selectedPaths
      = TreeUtil.collectSelectedPaths(tree, new TreePath(treeNode.getPath()));
    for (final TreePath expandedPath : selectedPaths) {
      final List<PathElement> path = createPath(expandedPath);
      if (path != null) {
        result.add(path);
      }
    }
    return result;
  }

  private static List<PathElement> createPath(final TreePath treePath) {
    final ArrayList<PathElement> result = new ArrayList<PathElement>();
    for (int i = 0; i < treePath.getPathCount(); i++) {
      final Object pathComponent = treePath.getPathComponent(i);
      if (pathComponent instanceof DefaultMutableTreeNode) {
        final Object userObject = ((DefaultMutableTreeNode)pathComponent).getUserObject();
        if (userObject instanceof NodeDescriptor) {
          final NodeDescriptor nodeDescriptor = ((NodeDescriptor)userObject);
          //nodeDescriptor.update();
          result.add(new PathElement(getDescriptorKey(nodeDescriptor), getDescriptorType(nodeDescriptor), nodeDescriptor.getIndex(), nodeDescriptor));
        }
        else {
          return null;
        }
      }
      else {
        return null;
      }
    }
    return result;
  }

  private static String getDescriptorKey(final NodeDescriptor nodeDescriptor) {
    if (nodeDescriptor instanceof AbstractTreeNode) {
      final Object value = ((AbstractTreeNode)nodeDescriptor).getValue();
      if (value instanceof PsiElement) {
        // for PsiElements only since they define toString() correctly
        return value.toString();
      }
    }
    return nodeDescriptor.toString();
  }

  private static String getDescriptorType(final NodeDescriptor nodeDescriptor) {
    return nodeDescriptor.getClass().getName();
  }

  public void applyTo(JTree tree) {
    applyExpanded(tree);
  }

  private void applyExpanded(final JTree tree) {
    for (final List<PathElement> myPath : myExpandedPaths) {
      applyTo(myPath, tree);
    }
  }

  public void applyTo(final JTree tree, final DefaultMutableTreeNode node) {
    applyExpanded(tree);
    applySelected(tree, node);
  }

  private void applySelected(final JTree tree, final DefaultMutableTreeNode node) {
    TreeUtil.unselect(tree, node);
    for (List<PathElement> pathElements : mySelectedPaths) {
      applySelectedTo(1, pathElements, tree.getModel().getRoot(), tree);
    }
  }


  private void applyTo(final List<PathElement> path, final JTree tree) {
    applyTo(0, path, tree.getModel().getRoot(), tree);
  }

  private DefaultMutableTreeNode findMatchedChild(DefaultMutableTreeNode parent, PathElement pathElement) {

    for (int j = 0; j < parent.getChildCount(); j++) {
      final TreeNode child = parent.getChildAt(j);
      if (!(child instanceof DefaultMutableTreeNode)) continue;
      final DefaultMutableTreeNode childNode = ((DefaultMutableTreeNode)child);
      final Object userObject = childNode.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) continue;
      final NodeDescriptor nodeDescriptor = ((NodeDescriptor)userObject);
      if (pathElement.matchedWithByObject(nodeDescriptor)) return childNode;
    }

    for (int j = 0; j < parent.getChildCount(); j++) {
      final TreeNode child = parent.getChildAt(j);
      if (!(child instanceof DefaultMutableTreeNode)) continue;
      final DefaultMutableTreeNode childNode = ((DefaultMutableTreeNode)child);
      final Object userObject = childNode.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) continue;
      final NodeDescriptor nodeDescriptor = ((NodeDescriptor)userObject);
      if (pathElement.matchedWith(nodeDescriptor)) return childNode;
    }

    for (int j = 0; j < parent.getChildCount(); j++) {
      final TreeNode child = parent.getChildAt(j);
      if (!(child instanceof DefaultMutableTreeNode)) continue;
      final DefaultMutableTreeNode childNode = ((DefaultMutableTreeNode)child);
      final Object userObject = childNode.getUserObject();
      if (!(userObject instanceof NodeDescriptor)) continue;
      final NodeDescriptor nodeDescriptor = ((NodeDescriptor)userObject);
      if (pathElement.matchedWithByIndex(nodeDescriptor)) return childNode;
    }

    return null;

  }

  private boolean applyTo(final int positionInPath, final List<PathElement> path, final Object root, JTree tree) {
    if (!(root instanceof DefaultMutableTreeNode)) return false;

    final DefaultMutableTreeNode treeNode = ((DefaultMutableTreeNode)root);

    final Object userObject = treeNode.getUserObject();

    if (!(userObject instanceof NodeDescriptor)) return false;

    final NodeDescriptor nodeDescriptor = ((NodeDescriptor)userObject);

    final PathElement pathElement = path.get(positionInPath);

    if (!pathElement.matchedWith(nodeDescriptor)) return false;

    final TreePath currentPath = new TreePath(treeNode.getPath());

    if (!tree.isExpanded(currentPath)) {
      tree.expandPath(currentPath);
    }

    if (positionInPath == path.size() - 1) {
      return true;
    }

    for (int j = 0; j < treeNode.getChildCount(); j++) {
      final TreeNode child = treeNode.getChildAt(j);
      final boolean resultFromChild = applyTo(positionInPath + 1, path, child, tree);
      if (resultFromChild) {
        break;
      }
    }

    return true;
  }

  private void applySelectedTo(final int positionInPath, final List<PathElement> path, final Object root, JTree tree) {
    if (!(root instanceof DefaultMutableTreeNode)) return;

    final DefaultMutableTreeNode treeNode = ((DefaultMutableTreeNode)root);

    if (positionInPath == path.size()) {
      TreeUtil.selectPath(tree, new TreePath(treeNode.getPath()));
      return;
    }

    final DefaultMutableTreeNode matchedChild = findMatchedChild(treeNode, path.get(positionInPath));
    if (matchedChild != null) {
      applySelectedTo(positionInPath + 1, path, matchedChild, tree);
    }
  }

}

