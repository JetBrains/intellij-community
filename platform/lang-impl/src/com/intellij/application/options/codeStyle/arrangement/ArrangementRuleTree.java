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
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementMatcherSettings;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
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

  @NotNull private final List<ArrangementMatcherEditingListener>       myListeners      = new ArrayList<ArrangementMatcherEditingListener>();
  @NotNull private final TreeSelectionModel                            mySelectionModel = new MySelectionModel();
  @NotNull private final TIntObjectHashMap<ArrangementNodeComponent>   myRenderers      = new TIntObjectHashMap<ArrangementNodeComponent>();
  @NotNull private final TIntObjectHashMap<ArrangementMatcherSettings> mySettings       =
    new TIntObjectHashMap<ArrangementMatcherSettings>();

  @NotNull private final ArrangementStandardSettingsAware myFilter;
  @NotNull private final Tree                             myTree;
  @NotNull private final ArrangementNodeDisplayManager    myDisplayManager;
  @NotNull private final ArrangementNodeComponentFactory  myFactory;

  private boolean mySkipSelectionChange;

  public ArrangementRuleTree(@NotNull ArrangementStandardSettingsAware filter) {
    myFilter = filter;
    myDisplayManager = new ArrangementNodeDisplayManager(filter);
    myFactory = new ArrangementNodeComponentFactory(myDisplayManager);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode();
    DefaultTreeModel treeModel = new DefaultTreeModel(root);
    myTree = new Tree(treeModel) {
      @Override
      protected void setExpandedState(TreePath path, boolean state) {
        // Don't allow node collapse
        if (state) {
          super.setExpandedState(path, state);
        }
      }

      @Override
      protected boolean isAlwaysPaintRowBackground() {
        return false;
      }

      @Override
      protected void processMouseEvent(MouseEvent e) {
        // JTree selects a node on mouse click at the same row (even outside the node bounds). We don't want to support
        // such selection because selected nodes are highlighted at the rule tree, so, it produces a 'blink' effect.
        mySkipSelectionChange = e.getClickCount() > 0 && getNodeComponentAt(e.getLocationOnScreen()) == null;
        try {
          super.processMouseEvent(e);
          if (mySkipSelectionChange) {
            notifyEditingListeners(null);
          }
        }
        finally {
          mySkipSelectionChange = false;
        }
      }
    };
    myTree.setSelectionModel(mySelectionModel);
    mySelectionModel.addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        setSelection(e.getOldLeadSelectionPath(), false);
        setSelection(e.getNewLeadSelectionPath(), true);
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }
    });
    myTree.putClientProperty(DataManager.CLIENT_PROPERTY_DATA_PROVIDER, new DataProvider() {
      @Override
      public Object getData(@NonNls String dataId) {
        if (ArrangementSettingsUtil.DISPLAY_MANAGER.is(dataId)) {
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

    ArrangementSettingsCompositeNode constants = new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND);
    constants.addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC));
    constants.addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.STATIC));
    constants.addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.FINAL));

    ArrangementSettingsCompositeNode privateFields = new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND);
    privateFields.addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE));

    HierarchicalArrangementSettingsNode fields = new HierarchicalArrangementSettingsNode(new ArrangementSettingsAtomNode(
      ArrangementSettingType.TYPE, ArrangementEntryType.FIELD
    ));
    fields.addChild(new HierarchicalArrangementSettingsNode(constants));
    fields.addChild(new HierarchicalArrangementSettingsNode(privateFields));
    int row = map(root, fields, null, 0);

    HierarchicalArrangementSettingsNode methods = new HierarchicalArrangementSettingsNode(new ArrangementSettingsAtomNode(
      ArrangementSettingType.TYPE, ArrangementEntryType.METHOD
    ));
    methods.addChild(new HierarchicalArrangementSettingsNode(new ArrangementSettingsAtomNode(
      ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC
    )));
    methods.addChild(new HierarchicalArrangementSettingsNode(new ArrangementSettingsAtomNode(
      ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE
    )));
    map(root, methods, null, row);

    expandAll(myTree, new TreePath(root));
    myTree.setRootVisible(false);
    myTree.setShowsRootHandles(false);
    myTree.setCellRenderer(new MyCellRenderer());
  }

  private void setSelection(@Nullable final TreePath path, boolean selected) {
    if (path == null) {
      return;
    }
    
    for (TreePath p = path; p != null; p = p.getParentPath()) {
      int row = myTree.getRowForPath(p);
      if (row < 0) {
        return;
      }
      ArrangementNodeComponent component = myRenderers.get(row);
      if (component != null) {
        component.setSelected(selected);
        repaintComponent(component);
      }
    }
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

  private int map(@NotNull DefaultMutableTreeNode parentTreeNode,
                  @NotNull HierarchicalArrangementSettingsNode settingsNode,
                  @Nullable ArrangementMatcherSettings template,
                  int row)
  {
    DefaultMutableTreeNode childTreeNode = new DefaultMutableTreeNode(settingsNode.getCurrent());
    parentTreeNode.add(childTreeNode);
    List<HierarchicalArrangementSettingsNode> children = settingsNode.getChildren();
    ArrangementMatcherSettings settings = template == null ? new ArrangementMatcherSettings() : template.clone();
    settings.addCondition(settingsNode.getCurrent());
    if (children.isEmpty()) {
      mySettings.put(row, settings);
      return row + 1;
    }
    else {
      row++;
      for (HierarchicalArrangementSettingsNode node : children) {
        row = map(childTreeNode, node, settings, row);
      }
      return row;
    }
  }

  public void addEditingListener(@NotNull ArrangementMatcherEditingListener listener) {
    myListeners.add(listener);
  }
  
  /**
   * @return    matcher settings for the selected tree row(s) if any; null otherwise
   */
  @Nullable
  public ArrangementMatcherSettings getActiveSettings() {
    TreePath[] paths = mySelectionModel.getSelectionPaths();
    if (paths == null) {
      return null;
    }
    for (int i = paths.length - 1; i >= 0; i--) {
      int row = myTree.getRowForPath(paths[i]);
      ArrangementMatcherSettings settings = mySettings.get(row);
      if (settings != null) {
        return settings;
      }
    }
    return null;
  }
  
  @NotNull
  public Tree getTreeComponent() {
    return myTree;
  }

  @NotNull
  private ArrangementNodeComponent getNodeComponentAt(int row, @NotNull ArrangementSettingsNode node) {
    ArrangementNodeComponent result = myRenderers.get(row);
    if (result == null) {
      myRenderers.put(row, result = myFactory.getComponent(node));
    }
    return result;
  }

  private void onMouseClicked(@NotNull MouseEvent e) {
    ArrangementNodeComponent component = getNodeComponentAt(e.getLocationOnScreen());
    if (component != null) {
      return;
    }
    // Clear selection
    mySelectionModel.clearSelection();
    myRenderers.forEachValue(new TObjectProcedure<ArrangementNodeComponent>() {
      @Override
      public boolean execute(ArrangementNodeComponent node) {
        node.setSelected(false);
        return true;
      }
    });
    myTree.repaint();
  }
  
  @Nullable
  private ArrangementNodeComponent getNodeComponentAt(Point screenLocation) {
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
        return midVal.getNodeComponentAt(RelativePoint.fromScreen(screenLocation));
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

  private void notifyEditingListeners(@Nullable ArrangementMatcherSettings settings) {
    for (ArrangementMatcherEditingListener listener : myListeners) {
      if (settings == null) {
        listener.stopEditing();
      }
      else {
        listener.startEditing(settings);
      }
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
      return getNodeComponentAt(row, node).getUiComponent();
    }
  }
  
  private class MySelectionModel extends DefaultTreeSelectionModel {

    @Override
    public void setSelectionPath(TreePath path) {
      if (!mySkipSelectionChange) {
        super.setSelectionPath(path);
        notifyEditingListeners(getActiveSettings());
      }
    }
  }
}
