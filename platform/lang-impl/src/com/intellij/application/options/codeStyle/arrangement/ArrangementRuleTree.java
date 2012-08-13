/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.application.options.codeStyle.arrangement;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 8/10/12 2:10 PM
 */
public class ArrangementRuleTree {

  @NotNull private final TIntObjectHashMap<ArrangementNodeComponent> myRenderers = new TIntObjectHashMap<ArrangementNodeComponent>();

  @NotNull private final DefaultTreeModel                 myTreeModel;
  @NotNull private final ArrangementStandardSettingsAware myFilter;
  @NotNull private final Tree                             myTree;
  @NotNull private final ArrangementNodeDisplayManager    myDisplayManager;
  @NotNull private final ArrangementNodeComponentFactory  myFactory;

  @Nullable private ArrangementNodeComponent myPrevComponentUnderMouse;

  public ArrangementRuleTree(@NotNull ArrangementStandardSettingsAware filter) {
    myFilter = filter;
    myDisplayManager = new ArrangementNodeDisplayManager(filter);
    myFactory = new ArrangementNodeComponentFactory(myDisplayManager);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    myTreeModel = new DefaultTreeModel(root);
    myTree = new Tree(myTreeModel) {
      @Override
      protected void setExpandedState(TreePath path, boolean state) {
        // Don't allow node collapse
        if (state) {
          super.setExpandedState(path, state);
        }
      }
    };
    // Don't allow row selection as we're interested in particular row nodes.
    myTree.setSelectionModel(null);

    myTree.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        onMouseMoved(e);
      }
    });
    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (ArrangementSettingsUtil.NODE_COMPONENT.is(dataId)) {
          return myPrevComponentUnderMouse;
        }
        else if (ArrangementSettingsUtil.DISPLAY_MANAGER.is(dataId)) {
          return myDisplayManager;
        }
        else if (ArrangementSettingsUtil.FILTER.is(dataId)) {
          return myFilter;
        }
        else if (ArrangementSettingsUtil.TREE.is(dataId)) {
          return myTree;
        }
        return null;
      }
    });

    // TODO den remove
    List<ArrangementSettingsNode> children = new ArrayList<ArrangementSettingsNode>();
    children.add(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC));
    children.add(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.STATIC));
    children.add(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.FINAL));

    HierarchicalArrangementSettingsNode settingsNode = new HierarchicalArrangementSettingsNode(new ArrangementSettingsAtomNode(
      ArrangementSettingType.TYPE, ArrangementEntryType.FIELD
    ));
    ArrangementSettingsCompositeNode modifiers = new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND);
    for (ArrangementSettingsNode child : children) {
      modifiers.addOperand(child);
    }
    settingsNode.addChild(new HierarchicalArrangementSettingsNode(modifiers));
    //ArrangementSettingsNode node = ArrangementSettingsUtil.buildTreeStructure(settingsNode);
    if (settingsNode != null) {
      map(root, settingsNode);
    }

    expandAll(myTree, new TreePath(root));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setCellRenderer(new MyCellRenderer());
  }

  private static void expandAll(Tree tree, TreePath parent) {
    // Traverse children
    TreeNode node = (TreeNode)parent.getLastPathComponent();
    if (node.getChildCount() >= 0) {
      for (Enumeration e = node.children(); e.hasMoreElements(); ) {
        TreeNode n = (TreeNode)e.nextElement();
        TreePath path = parent.pathByAddingChild(n);
        expandAll(tree, path);
      }
    }

    // Expansion or collapse must be done bottom-up
    tree.expandPath(parent);
  }

  private static void map(@NotNull DefaultMutableTreeNode parentTreeNode, @NotNull HierarchicalArrangementSettingsNode settingsNode) {
    DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode(settingsNode.getCurrent());
    parentTreeNode.add(childTreeNode);
    for (HierarchicalArrangementSettingsNode node : settingsNode.getChildren()) {
      map(childTreeNode, node);
    }
  }
  
  @NotNull
  public Tree getTreeComponent() {
    return myTree;
  }

  @NotNull
  private ArrangementNodeComponent getComponent(int row, @NotNull ArrangementSettingsNode node) {
    ArrangementNodeComponent result = myRenderers.get(row);
    if (result == null) {
      myRenderers.put(row, result = myFactory.getComponent(node));
    }
    return result;
  }

  private void onMouseMoved(@NotNull MouseEvent e) {
    ArrangementNodeComponent component = getComponent(e.getLocationOnScreen());
    if (component == myPrevComponentUnderMouse) {
      return;
    }
    if (myPrevComponentUnderMouse != null) {
      repaintComponent(myPrevComponentUnderMouse);
    }
    if (component != null) {
      repaintComponent(component);
    }
    myPrevComponentUnderMouse = component;
  }

  @Nullable
  private ArrangementNodeComponent getComponent(Point screenLocation) {
    int low = 0;
    int high = myTree.getRowCount() - 1;

    while (low <= high) {
      int mid = (low + high) >>> 1;
      ArrangementNodeComponent midVal = myRenderers.get(mid);
      if (midVal == null) {
        return null;
      }
      Rectangle bounds = midVal.getScreenBounds();
      if (bounds == null) {
        return null;
      }
      if (bounds.contains(screenLocation)) {
        return midVal.getComponentAt(RelativePoint.fromScreen(screenLocation));
      }
      else if (bounds.y > screenLocation.y) {
        high = mid - 1;
      }
      else if (bounds.y + bounds.height <= screenLocation.y) {
        low = mid + 1;
      }
      else {
        return null;
      }
    }
    return null;
  }

  private void repaintComponent(@NotNull ArrangementNodeComponent component) {
    Rectangle bounds = component.getScreenBounds();
    if (bounds != null) {
      Point location = bounds.getLocation();
      SwingUtilities.convertPointFromScreen(location, myTree);
      myTree.repaint(location.x, location.y, bounds.width, bounds.height);
    }
  }

  private class MyCellRenderer implements TreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus)
    {
      ArrangementSettingsNode node = (ArrangementSettingsNode)((DefaultMutableTreeNode)value).getUserObject();
      return getComponent(row, node).getUiComponent();
    }
  }
}
