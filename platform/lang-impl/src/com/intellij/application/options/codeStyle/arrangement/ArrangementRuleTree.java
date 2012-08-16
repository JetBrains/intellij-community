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

import com.intellij.psi.codeStyle.arrangement.match.ArrangementEntryType;
import com.intellij.psi.codeStyle.arrangement.match.ArrangementModifier;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingType;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsAtomNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsCompositeNode;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementSettingsNode;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsGrouper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import gnu.trove.TIntObjectHashMap;
import gnu.trove.TObjectProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 8/10/12 2:10 PM
 */
public class ArrangementRuleTree {

  @NotNull private static final JLabel EMPTY_RENDERER = new JLabel("");

  @NotNull private final List<ArrangementRuleSelectionListener> myListeners           = new ArrayList<ArrangementRuleSelectionListener>();
  @NotNull private final TreeSelectionModel                     mySelectionModel      = new MySelectionModel();
  @NotNull private final MyModelChangeListener                  myModelChangeListener = new MyModelChangeListener();

  @NotNull private final TIntObjectHashMap<ArrangementNodeComponent>    myRenderers =
    new TIntObjectHashMap<ArrangementNodeComponent>();
  @NotNull private final TIntObjectHashMap<ArrangementRuleEditingModel> myModels    =
    new TIntObjectHashMap<ArrangementRuleEditingModel>();

  @NotNull private final DefaultTreeModel                myTreeModel;
  @NotNull private final Tree                            myTree;
  @NotNull private final ArrangementNodeComponentFactory myFactory;

  private boolean mySkipSelectionChange;

  public ArrangementRuleTree(@NotNull ArrangementSettingsGrouper grouper, @NotNull ArrangementNodeDisplayManager displayManager) {
    myFactory = new ArrangementNodeComponentFactory(displayManager);
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
            notifySelectionListeners(null);
          }
        }
        finally {
          mySkipSelectionChange = false;
        }
      }
    };
    myTree.setSelectionModel(mySelectionModel);
    myTree.setRootVisible(false);
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
    
    List<ArrangementSettingsNode> rules = new ArrayList<ArrangementSettingsNode>();
    rules.add(new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND)
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.TYPE, ArrangementEntryType.FIELD))
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC))
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.STATIC))
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.FINAL)));
    rules.add(new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND)
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.TYPE, ArrangementEntryType.FIELD))
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE)));
    rules.add(new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND)
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD))
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC)));
    rules.add(new ArrangementSettingsCompositeNode(ArrangementSettingsCompositeNode.Operator.AND)
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD))
                .addOperand(new ArrangementSettingsAtomNode(ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE)));
    map(root, rules, grouper);

    expandAll(myTree, new TreePath(root));
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

  private void map(@NotNull DefaultMutableTreeNode root,
                   @NotNull List<ArrangementSettingsNode> settings,
                   @NotNull ArrangementSettingsGrouper grouper)
  {
    ArrangementRuleEditingModelBuilder builder = new ArrangementRuleEditingModelBuilder();
    for (ArrangementSettingsNode setting : settings) {
      builder.build(setting, myTree, root, grouper, myModels);
    }
    myModels.forEachValue(new TObjectProcedure<ArrangementRuleEditingModel>() {
      @Override
      public boolean execute(ArrangementRuleEditingModel model) {
        model.addListener(myModelChangeListener); 
        return true;
      }
    });
  }

  public void addEditingListener(@NotNull ArrangementRuleSelectionListener listener) {
    myListeners.add(listener);
  }
  
  /**
   * @return    matcher model for the selected tree row(s) if any; null otherwise
   */
  @Nullable
  public ArrangementRuleEditingModel getActiveModel() {
    TreePath[] paths = mySelectionModel.getSelectionPaths();
    if (paths == null) {
      return null;
    }
    
    // There is a possible case that particular settings node is represented on multiple rows and that non-leaf nodes are served
    // for more than one rule. No model is registered for them then and we want just to skip them.
    for (int i = paths.length - 1; i >= 0; i--) {
      int row = myTree.getRowForPath(paths[i]);
      ArrangementRuleEditingModel model = myModels.get(row);
      if (model != null) {
        return model;
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

  private void notifySelectionListeners(@Nullable ArrangementRuleEditingModel model) {
    for (ArrangementRuleSelectionListener listener : myListeners) {
      if (model == null) {
        listener.selectionRemoved();
      }
      else {
        listener.onSelected(model);
      }
    }
  }

  private void onModelChange(@NotNull TreeNode topMost, @NotNull TreeNode bottomMost) {
    mySkipSelectionChange = true;
    try {
      for (DefaultMutableTreeNode node = (DefaultMutableTreeNode)bottomMost; node != null; node = (DefaultMutableTreeNode)node.getParent()) {
        TreePath path = new TreePath(node.getPath());
        int row = myTree.getRowForPath(path);
        myRenderers.remove(row);
        myTreeModel.nodeChanged(node);
        mySelectionModel.addSelectionPath(path);
        getNodeComponentAt(row, (ArrangementSettingsNode)node.getUserObject()).setSelected(true);
        if (node == topMost) {
          break;
        }
      }
    }
    finally {
      mySkipSelectionChange = false;
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
                                                  boolean hasFocus) {
      if (row < 0) {
        return EMPTY_RENDERER;
      }
      ArrangementSettingsNode node = (ArrangementSettingsNode)((DefaultMutableTreeNode)value).getUserObject();
      return getNodeComponentAt(row, node).getUiComponent();
    }
  }
  
  private class MySelectionModel extends DefaultTreeSelectionModel {

    @Override
    public void addSelectionPath(TreePath path) {
      if (!mySkipSelectionChange) {
        super.addSelectionPath(path);
      }
    }

    @Override
    public void setSelectionPath(TreePath path) {
      if (!mySkipSelectionChange) {
        super.setSelectionPath(path);
        notifySelectionListeners(getActiveModel());
      }
    }
  }
  
  private class MyModelChangeListener implements ArrangementRuleEditingModel.Listener {
    @Override
    public void onChanged(@NotNull TreeNode topMost, @NotNull TreeNode bottomMost) {
      onModelChange(topMost, bottomMost); 
    }
  }
}
