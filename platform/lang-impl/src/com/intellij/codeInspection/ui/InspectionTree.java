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

/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Nov 4, 2001
 * Time: 5:19:35 PM
 */
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.DescriptorProviderInspection;
import com.intellij.codeInspection.ex.InspectionTool;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.profile.codeInspection.ui.InspectionsConfigTreeComparator;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TreeSpeedSearch;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.Convertor;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeExpansionEvent;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.event.TreeWillExpandListener;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.ExpandVetoException;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public class InspectionTree extends Tree {
  private final HashSet<Object> myExpandedUserObjects;
  private SelectionPath mySelectionPath;
  private static final RefEntity[] EMPTY_ELEMENTS_ARRAY = new RefEntity[0];
  private static final ProblemDescriptor[] EMPTY_DESCRIPTORS = new ProblemDescriptor[0];

  public InspectionTree(final Project project) {
    super(new InspectionRootNode(project));

    setCellRenderer(new CellRenderer());//project));
    setShowsRootHandles(true);
    UIUtil.setLineStyleAngled(this);
    addTreeWillExpandListener(new ExpandListener());

    myExpandedUserObjects = new HashSet<Object>();
    myExpandedUserObjects.add(project);

    TreeUtil.installActions(this);
    new TreeSpeedSearch(this, new Convertor<TreePath, String>() {
      public String convert(TreePath o) {
        return InspectionsConfigTreeComparator.getDisplayTextToSort(o.getLastPathComponent().toString());
      }
    });

    addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreePath newSelection = e.getNewLeadSelectionPath();
        if (newSelection != null) {
          mySelectionPath = new SelectionPath(newSelection);
        }
      }
    });
  }

  public void removeAllNodes() {
    getRoot().removeAllChildren();
    nodeStructureChanged(getRoot());
  }

  public InspectionTreeNode getRoot() {
    return (InspectionTreeNode)getModel().getRoot();
  }

  @Nullable
  public InspectionTool getSelectedTool() {
    final TreePath[] paths = getSelectionPaths();
    if (paths == null) return null;
    InspectionTool tool = null;
    for (TreePath path : paths) {
      Object[] nodes = path.getPath();
      for (int j = nodes.length - 1; j >= 0; j--) {
        Object node = nodes[j];
        if (node instanceof InspectionNode) {
          if (tool == null) {
            tool = ((InspectionNode)node).getTool();
          }
          else if (tool != ((InspectionNode)node).getTool()) {
            return null;
          }
          break;
        }
      }
    }

    return tool;
  }

  @NotNull
  public RefEntity[] getSelectedElements() {
    TreePath[] selectionPaths = getSelectionPaths();
    if (selectionPaths != null) {
      final InspectionTool selectedTool = getSelectedTool();
      if (selectedTool == null) return EMPTY_ELEMENTS_ARRAY;

      List<RefEntity> result = new ArrayList<RefEntity>();
      for (TreePath selectionPath : selectionPaths) {
        final InspectionTreeNode node = (InspectionTreeNode)selectionPath.getLastPathComponent();
        addElementsInNode(node, result);
      }
      return result.toArray(new RefEntity[result.size()]);
    }
    return EMPTY_ELEMENTS_ARRAY;
  }

  private static void addElementsInNode(InspectionTreeNode node, List<RefEntity> out) {
    if (!node.isValid()) return;
    if (node instanceof RefElementNode) {
      final RefEntity element = ((RefElementNode)node).getElement();
      if (!out.contains(element)) {
        out.add(0, element);
      }
    }
    if (node instanceof ProblemDescriptionNode) {
      final RefEntity element = ((ProblemDescriptionNode)node).getElement();
      if (!out.contains(element)) {
        out.add(0, element);
      }
    }
    final Enumeration children = node.children();
    while (children.hasMoreElements()) {
      InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
      addElementsInNode(child, out);
    }    
  }

  public CommonProblemDescriptor[] getSelectedDescriptors() {
    final InspectionTool tool = getSelectedTool();
    if (getSelectionCount() == 0 || !(tool instanceof DescriptorProviderInspection)) return EMPTY_DESCRIPTORS;
    final TreePath[] paths = getSelectionPaths();
    final List<CommonProblemDescriptor> descriptors = new ArrayList<CommonProblemDescriptor>();
    for (TreePath path : paths) {
      Object node = path.getLastPathComponent();
      traverseDescriptors((InspectionTreeNode)node, descriptors);
    }
    return descriptors.toArray(new CommonProblemDescriptor[descriptors.size()]);
  }

  private static void traverseDescriptors(InspectionTreeNode node, List<CommonProblemDescriptor> descriptors){
    if (node instanceof ProblemDescriptionNode) {
      descriptors.add(((ProblemDescriptionNode)node).getDescriptor());
    }
    for(int i = node.getChildCount() - 1; i >= 0; i--){
      traverseDescriptors((InspectionTreeNode)node.getChildAt(i), descriptors);
    }
  }

  private void nodeStructureChanged(InspectionTreeNode node) {
    ((DefaultTreeModel)getModel()).nodeStructureChanged(node);
  }

  private class ExpandListener implements TreeWillExpandListener {
    public void treeWillExpand(TreeExpansionEvent event) throws ExpandVetoException {
      final InspectionTreeNode node = (InspectionTreeNode)event.getPath().getLastPathComponent();
      final Object userObject = node.getUserObject();
      //TODO: never re-sort
      if (node.isValid() && !myExpandedUserObjects.contains(userObject)) {
        sortChildren(node);
        nodeStructureChanged(node);
      }
      myExpandedUserObjects.add(userObject);
      // Smart expand
      if (node.getChildCount() == 1) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          public void run() {
            expandPath(new TreePath(node.getPath()));
          }
        });
      }
    }

    public void treeWillCollapse(TreeExpansionEvent event) throws ExpandVetoException {
      InspectionTreeNode node = (InspectionTreeNode)event.getPath().getLastPathComponent();
      myExpandedUserObjects.remove(node.getUserObject());
    }
  }

  public void restoreExpantionAndSelection() {
    restoreExpantion();
    if (mySelectionPath != null) {
      mySelectionPath.restore();
    }
  }

  private void restoreExpantion() {
    restoreExpantionStatus((InspectionTreeNode)getModel().getRoot());
  }


  private void restoreExpantionStatus(InspectionTreeNode node) {
    if (myExpandedUserObjects.contains(node.getUserObject())) {
      TreeNode[] pathToNode = node.getPath();
      expandPath(new TreePath(pathToNode));
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode childNode = (InspectionTreeNode)children.nextElement();
        restoreExpantionStatus(childNode);
      }
    }
  }

  private static class CellRenderer extends ColoredTreeCellRenderer {
    /*  private Project myProject;
      InspectionManagerEx myManager;
      public CellRenderer(Project project) {
        myProject = project;
        myManager = (InspectionManagerEx)InspectionManager.getInstance(myProject);
      }*/

    public void customizeCellRenderer(JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      InspectionTreeNode node = (InspectionTreeNode)value;

      append(node.toString(),
             patchAttr(node, appearsBold(node) ? SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES : getMainForegroundAttributes(node)));

      int problemCount = node.getProblemCount();
      if (!leaf) {
        append(" " + InspectionsBundle.message("inspection.problem.descriptor.count", problemCount), patchAttr(node, SimpleTextAttributes.GRAYED_ATTRIBUTES));
      }

      if (!node.isValid()) {
        append(" " + InspectionsBundle.message("inspection.invalid.node.text"), patchAttr(node, SimpleTextAttributes.ERROR_ATTRIBUTES));
      } else {
        setIcon(node.getIcon(expanded));
      }
    }

    public static SimpleTextAttributes patchAttr(InspectionTreeNode node, SimpleTextAttributes attributes) {
      if (node.isResolved()) {
        return new SimpleTextAttributes(attributes.getBgColor(), attributes.getFgColor(), attributes.getWaveColor(), attributes.getStyle() | SimpleTextAttributes.STYLE_STRIKEOUT);
      }
      return attributes;
    }

    private static SimpleTextAttributes getMainForegroundAttributes(InspectionTreeNode node) {
      SimpleTextAttributes foreground = SimpleTextAttributes.REGULAR_ATTRIBUTES;
      if (node instanceof RefElementNode) {
        RefEntity refElement = ((RefElementNode)node).getElement();

        if (refElement instanceof RefElement) {
          refElement = ((RefElement)refElement).getContainingEntry();
          if (((RefElement)refElement).isEntry() && ((RefElement)refElement).isPermanentEntry()) {
            foreground = new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, Color.blue);
          }
        }

      }
      final FileStatus nodeStatus = node.getNodeStatus();
      if (nodeStatus != FileStatus.NOT_CHANGED){
        foreground = new SimpleTextAttributes(foreground.getBgColor(), nodeStatus.getColor(), foreground.getWaveColor(), foreground.getStyle());
      }
      return foreground;
    }

    private static boolean appearsBold(Object node) {
      return ((InspectionTreeNode)node).appearsBold();
    }
  }

  public void sort() {
    sortChildren(getRoot());
  }

  private static void sortChildren(InspectionTreeNode node) {
    final List<TreeNode> children = TreeUtil.childrenToArray(node);
    Collections.sort(children, InspectionResultsViewComparator.getInstance());
    node.removeAllChildren();
    TreeUtil.addChildrenTo(node, children);
  }

  private class SelectionPath {
    private final Object[] myPath;
    private final int[] myIndicies;

    public SelectionPath(TreePath path) {
      myPath = path.getPath();
      myIndicies = new int[myPath.length];
      for (int i = 0; i < myPath.length - 1; i++) {
        InspectionTreeNode node = (InspectionTreeNode)myPath[i];
        myIndicies[i + 1] = getChildIndex(node, (InspectionTreeNode)myPath[i + 1]);
      }
    }

    private int getChildIndex(InspectionTreeNode node, InspectionTreeNode child) {
      int idx = 0;
      Enumeration children = node.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode ch = (InspectionTreeNode)children.nextElement();
        if (ch == child) break;
        idx++;
      }
      return idx;
    }

    public void restore() {
      getSelectionModel().removeSelectionPaths(getSelectionModel().getSelectionPaths());
      TreeUtil.selectPath(InspectionTree.this, restorePath());
    }

    private TreePath restorePath() {
      ArrayList<Object> newPath = new ArrayList<Object>();

      newPath.add(getModel().getRoot());
      restorePath(newPath, 1);

      return new TreePath(newPath.toArray(new InspectionTreeNode[newPath.size()]));
    }

    private void restorePath(ArrayList<Object> newPath, int idx) {
      if (idx >= myPath.length) return;
      InspectionTreeNode oldNode = (InspectionTreeNode)myPath[idx];

      InspectionTreeNode newRoot = (InspectionTreeNode)newPath.get(idx - 1);


      InspectionResultsViewComparator comparator = InspectionResultsViewComparator.getInstance();
      Enumeration children = newRoot.children();
      while (children.hasMoreElements()) {
        InspectionTreeNode child = (InspectionTreeNode)children.nextElement();
        if (comparator.compare(child, oldNode) == 0) {
          newPath.add(child);
          restorePath(newPath, idx + 1);
          return;
        }
      }

      // Exactly same element not found. Trying to select somewhat near.
      int count = newRoot.getChildCount();
      if (count > 0) {
        if (myIndicies[idx] < count) {
          newPath.add(newRoot.getChildAt(myIndicies[idx]));
        }
        else {
          newPath.add(newRoot.getChildAt(count - 1));
        }
      }
    }
  }
}
