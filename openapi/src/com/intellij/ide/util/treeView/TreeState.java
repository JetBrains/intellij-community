package com.intellij.ide.util.treeView;

import com.intellij.openapi.util.*;
import com.intellij.util.ui.tree.TreeUtil;
import org.jdom.Element;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TreeState implements JDOMExternalizable{
private static final String PATH = "PATH";
private static final String PATH_ELEMENT = "PATH_ELEMENT";

static class PathElement implements JDOMExternalizable{
  public String myItemId;
  public String myItemType;

  public PathElement(final String itemId, final String itemType) {
    myItemId = itemId;
    myItemType = itemType;
  }

  public PathElement() {
  }

  public boolean matchedWith(NodeDescriptor nodeDescriptor) {
    return Comparing.equal(myItemId, nodeDescriptor.toString()) &&
           Comparing.equal(myItemType, nodeDescriptor.getClass().getName());
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }
}

private final List<List<PathElement>> myPaths;

private TreeState(List<List<PathElement>> paths) {
  myPaths = paths;
}

public TreeState() {
  myPaths = new ArrayList<List<PathElement>>();
}

public void readExternal(Element element) throws InvalidDataException {
  myPaths.clear();
  final List paths = element.getChildren(PATH);
  for (Iterator iterator = paths.iterator(); iterator.hasNext();) {
    Element xmlPathElement = (Element)iterator.next();
    myPaths.add(readPath(xmlPathElement));
  }
}

private List<PathElement> readPath(final Element xmlPathElement) throws InvalidDataException {
  final ArrayList<PathElement> result = new ArrayList<PathElement>();
  final List elements = xmlPathElement.getChildren(PATH_ELEMENT);
  for (Iterator iterator = elements.iterator(); iterator.hasNext();) {
    Element xmlPathElementElement = (Element)iterator.next();
    final PathElement pathElement = new PathElement();
    pathElement.readExternal(xmlPathElementElement);
    result.add(pathElement);
  }
  return result;
}

public void writeExternal(Element element) throws WriteExternalException {
  for (Iterator<List<PathElement>> iterator = myPaths.iterator(); iterator.hasNext();) {
    List<PathElement> path = iterator.next();
    final Element pathElement = new Element(PATH);
    writeExternal(pathElement, path);
    element.addContent(pathElement);
  }
}

private void writeExternal(final Element pathXmlElement, final List<PathElement> path) throws WriteExternalException {
  for (Iterator<PathElement> iterator = path.iterator(); iterator.hasNext();) {
    final Element pathXmlElementElement = new Element(PATH_ELEMENT);
    PathElement pathElement = iterator.next();
    pathElement.writeExternal(pathXmlElementElement);
    pathXmlElement.addContent(pathXmlElementElement);
  }
}

public static TreeState createOn(JTree tree) {
  return new TreeState(createPaths(tree));
}

private static List<List<PathElement>> createPaths(final JTree tree) {
  final ArrayList<List<PathElement>> result = new ArrayList<List<PathElement>>();
  final java.util.List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(tree);
  for (Iterator<TreePath> iterator = expandedPaths.iterator(); iterator.hasNext();) {
    final List<PathElement> path = createPath(iterator.next());
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
        nodeDescriptor.update();
        final String key = nodeDescriptor.toString();
        final String type = nodeDescriptor.getClass().getName();
        result.add(new PathElement(key, type));
      } else {
        return null;
      }
    } else {
      return null;
    }
  }
  return result;
}

public void applyTo(JTree tree) {
  for (Iterator<List<PathElement>> iterator = myPaths.iterator(); iterator.hasNext();) {
    applyTo(iterator.next(), tree);
  }
}

private void applyTo(final List<PathElement> path, final JTree tree) {
  applyTo(0, path, tree.getModel().getRoot(), tree);
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
}

