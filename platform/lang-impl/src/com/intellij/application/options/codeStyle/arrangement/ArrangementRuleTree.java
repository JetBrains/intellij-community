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
import com.intellij.psi.codeStyle.arrangement.model.*;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementSettingsGrouper;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Consumer;
import gnu.trove.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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
  @NotNull private final MyModelNodesRefresher                  myModelNodesRefresher = new MyModelNodesRefresher();

  @NotNull private final TIntObjectHashMap<ArrangementNodeComponent>        myRenderers =
    new TIntObjectHashMap<ArrangementNodeComponent>();
  @NotNull private final TIntObjectHashMap<ArrangementRuleEditingModelImpl> myModels    =
    new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();

  @NotNull private final ArrangementTreeNode             myRoot;
  @NotNull private final DefaultTreeModel                myTreeModel;
  @NotNull private final Tree                            myTree;
  @NotNull private final ArrangementNodeComponentFactory myFactory;

  private boolean myExplicitSelectionChange;
  private boolean mySkipSelectionChange;

  public ArrangementRuleTree(@NotNull ArrangementSettingsGrouper grouper, @NotNull ArrangementNodeDisplayManager displayManager) {
    myFactory = new ArrangementNodeComponentFactory(displayManager, new Consumer<ArrangementAtomMatchCondition>() {
      @Override
      public void consume(@NotNull ArrangementAtomMatchCondition setting) {
        removeConditionFromActiveModel(setting);
      }
    });
    myRoot = new ArrangementTreeNode(null);
    myTreeModel = new DefaultTreeModel(myRoot);
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
        if (myExplicitSelectionChange) {
          return;
        }
        TreePath[] paths = e.getPaths();
        if (paths == null) {
          return;
        }
        for (int i = 0; i < paths.length; i++) {
          onSelectionChange(paths[i], e.isAddedPath(i));
        }
      }
    });
    myTree.addMouseMotionListener(new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        onMouseMoved(e);
      }
    });
    myTree.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        onMouseClicked(e);
      }
    });
    
    // Setup the tree to perform rule-aware navigation via up/down arrow keys.
    myTree.getActionMap().put("selectNext", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selectNextRule();
      }
    });
    myTree.getActionMap().put("selectPrevious", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        selectPreviousRule();
      }
    });
    
    List<ArrangementMatchCondition> rules = new ArrayList<ArrangementMatchCondition>();
    rules.add(new ArrangementCompositeMatchCondition(ArrangementCompositeMatchCondition.Operator.AND)
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.FIELD))
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC))
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.STATIC))
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.FINAL)));
    rules.add(new ArrangementCompositeMatchCondition(ArrangementCompositeMatchCondition.Operator.AND)
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.FIELD))
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE)));
    rules.add(new ArrangementCompositeMatchCondition(ArrangementCompositeMatchCondition.Operator.AND)
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD))
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PUBLIC)));
    rules.add(new ArrangementCompositeMatchCondition(ArrangementCompositeMatchCondition.Operator.AND)
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.TYPE, ArrangementEntryType.METHOD))
                .addOperand(new ArrangementAtomMatchCondition(ArrangementSettingType.MODIFIER, ArrangementModifier.PRIVATE)));
    map(myRoot, rules, grouper);

    expandAll(myTree, new TreePath(myRoot));
    myTree.setShowsRootHandles(false);
    myTree.setCellRenderer(new MyCellRenderer());
  }

  private void selectPreviousRule() {
    ArrangementTreeNode currentSelectionBottom = getCurrentSelectionBottom();

    if (currentSelectionBottom == null) {
      return;
    }
    
    for (ArrangementTreeNode parent = currentSelectionBottom.getParent();
         parent != null;
         currentSelectionBottom = parent, parent = parent.getParent())
    {
      int i = parent.getIndex(currentSelectionBottom);
      if (i <= 0) {
        continue;
      }
      ArrangementTreeNode toSelect = parent.getChildAt(i - 1);
      while (toSelect.getChildCount() > 0) {
        toSelect = toSelect.getLastChild();
      }
      mySelectionModel.setSelectionPath(new TreePath(toSelect.getPath()));
      break;
    }
  }

  private void selectNextRule() {
    ArrangementTreeNode currentSelectionBottom = getCurrentSelectionBottom();
    if (currentSelectionBottom == null) {
      if (myRoot.getChildCount() > 0) {
        mySelectionModel.setSelectionPath(new TreePath(myRoot.getFirstChild().getPath()));
      }
      return;
    }

    for (ArrangementTreeNode parent = currentSelectionBottom.getParent();
         parent != null;
         currentSelectionBottom = parent, parent = parent.getParent())
    {
      int i = parent.getIndex(currentSelectionBottom);
      if (i < parent.getChildCount() - 1) {
        mySelectionModel.setSelectionPath(new TreePath(parent.getChildAt(i + 1).getPath()));
        break;
      }
    }
  }

  @Nullable
  private ArrangementTreeNode getCurrentSelectionBottom() {
    TreePath[] paths = mySelectionModel.getSelectionPaths();
    if (paths == null) {
      return null;
    }

    ArrangementTreeNode currentSelectionBottom = null;
    for (TreePath treePath : paths) {
      ArrangementTreeNode last = (ArrangementTreeNode)treePath.getLastPathComponent();
      if (last.getChildCount() <= 0) {
        currentSelectionBottom = last;
        break;
      }
    }

    if (currentSelectionBottom == null) {
      return null;
    }
    return currentSelectionBottom;
  }

  private void doClearSelection() {
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

  /**
   * Updates renderer's {@link ArrangementNodeComponent#setSelected(boolean) 'selected'} state on tree ndoe selection change.
   * 
   * @param path      changed selection path
   * @param selected  <code>true</code> if given path is selected now; <code>false</code> if given path was selected anymore
   */
  private void onSelectionChange(@Nullable final TreePath path, boolean selected) {
    if (path == null) {
      return;
    }
    
    for (TreePath p = path; p != null; p = p.getParentPath()) {
      int row = myTree.getRowForPath(p);
      if (row < 0) {
        row = ((ArrangementTreeNode)p.getLastPathComponent()).getRow();
      }
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
    if (node.getChildCount() > 0) {
      for (Enumeration e = node.children(); e.hasMoreElements(); ) {
        TreeNode n = (TreeNode)e.nextElement();
        TreePath path = parent.pathByAddingChild(n);
        expandAll(tree, path);
      }
    }

    // Expansion or collapse must be done bottom-up
    tree.expandPath(parent);
  }

  private void map(@NotNull ArrangementTreeNode root,
                   @NotNull List<ArrangementMatchCondition> matchConditions,
                   @NotNull ArrangementSettingsGrouper grouper)
  {
    ArrangementRuleEditingModelBuilder builder = new ArrangementRuleEditingModelBuilder();
    for (ArrangementMatchCondition matchCondition : matchConditions) {
      builder.build(matchCondition, myTree, root, grouper, myModels);
    }
    myModels.forEachValue(new TObjectProcedure<ArrangementRuleEditingModelImpl>() {
      @Override
      public boolean execute(ArrangementRuleEditingModelImpl model) {
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
  private ArrangementNodeComponent getNodeComponentAt(int row, @NotNull ArrangementMatchCondition node) {
    ArrangementNodeComponent result = myRenderers.get(row);
    if (result == null) {
      myRenderers.put(row, result = myFactory.getComponent(node));
    }
    return result;
  }

  private void removeConditionFromActiveModel(@NotNull ArrangementAtomMatchCondition condition) {
    ArrangementRuleEditingModel model = getActiveModel();
    if (model != null) {
      model.removeAndCondition(condition);
      notifySelectionListeners(model);
    }
  }

  private void onMouseMoved(@NotNull MouseEvent e) {
    ArrangementNodeComponent component = getNodeComponentAt(e.getLocationOnScreen());
    if (component == null) {
      return;
    }
    Rectangle changedScreenRectangle = component.handleMouseMove(e);
    if (changedScreenRectangle != null) {
      repaintScreenBounds(changedScreenRectangle);
    }
  }
  
  private void onMouseClicked(@NotNull MouseEvent e) {
    ArrangementNodeComponent component = getNodeComponentAt(e.getLocationOnScreen());
    if (component != null) {
      component.handleMouseClick(e);
      return;
    }
    // Clear selection
    doClearSelection();
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
      repaintScreenBounds(bounds);
    }
  }

  private void repaintScreenBounds(@NotNull Rectangle bounds) {
    Point location = bounds.getLocation();
    SwingUtilities.convertPointFromScreen(location, myTree);
    myTree.repaint(location.x, location.y, bounds.width, bounds.height);
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

  private void onModelChange(@NotNull ArrangementRuleEditingModelImpl model, @NotNull final TIntIntHashMap rowChanges) {
    // Refresh models.
    myModels.forEachValue(myModelNodesRefresher);

    // Shift row-based caches.
    final TIntObjectHashMap<ArrangementRuleEditingModelImpl> changedModelMappings =
      new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();
    final TIntObjectHashMap<ArrangementNodeComponent> changedRendererMappings = new TIntObjectHashMap<ArrangementNodeComponent>();
    rowChanges.forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(int oldRow, int newRow) {
        ArrangementRuleEditingModelImpl m = myModels.remove(oldRow);
        if (m != null) {
          changedModelMappings.put(newRow, m);
        }

        ArrangementNodeComponent renderer = myRenderers.remove(oldRow);
        if (renderer != null) {
          changedRendererMappings.put(newRow, renderer);
        }
        return true;
      }
    });
    putAll(changedModelMappings, myModels);
    putAll(changedRendererMappings, myRenderers);

    // Perform necessary actions for the changed model.
    ArrangementTreeNode topMost = model.getTopMost();
    ArrangementTreeNode bottomMost = model.getBottomMost();
    expandAll(myTree, new TreePath(myTreeModel.getRoot()));
    doClearSelection();
    myExplicitSelectionChange = true;
    try {
      for (ArrangementTreeNode node = bottomMost; node != null; node = node.getParent()) {
        TreePath path = new TreePath(node.getPath());
        int row = myTree.getRowForPath(path);
        myRenderers.remove(row);
        myTreeModel.nodeChanged(node);
        mySelectionModel.addSelectionPath(path);
        ArrangementMatchCondition matchCondition = node.getBackingSetting();
        if (matchCondition != null) {
          getNodeComponentAt(row, matchCondition).setSelected(true);
        }
        if (node == topMost) {
          break;
        }
      }
    }
    finally {
      myExplicitSelectionChange = false;
    }
  }

  private static <T> void putAll(@NotNull TIntObjectHashMap<T> from, @NotNull final TIntObjectHashMap<T> to) {
    from.forEachEntry(new TIntObjectProcedure<T>() {
      @Override
      public boolean execute(int key, T value) {
        to.put(key, value);
        return true;
      }
    });
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
      ArrangementMatchCondition node = ((ArrangementTreeNode)value).getBackingSetting();
      if (node == null) {
        return EMPTY_RENDERER;
      }

      if (row < 0) {
        return myFactory.getComponent(node).getUiComponent();
      }
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
      if (mySkipSelectionChange) {
        return;
      }
      
      clearSelection();
      ArrangementTreeNode component = (ArrangementTreeNode)path.getLastPathComponent();
      
      // Select all nodes which correspond to the first rule under the node denoted by the given path
      // in case when non-leaf is selected.
      if (component.getChildCount() > 0) {
        for (ArrangementTreeNode node = component.getChildAt(0); node != null; node = node.getChildAt(0)) {
          addSelectionPath(new TreePath(node.getPath()));
          if (node.getChildCount() <= 0) {
            break;
          }
        }
      }
      
      // Select the node itself.
      super.addSelectionPath(path);
      
      // Select parent nodes from the same rule.
      for (ArrangementTreeNode node = component.getParent(); node != null && node != myRoot; node = node.getParent()) {
        addSelectionPath(new TreePath(node.getPath()));
      }
      
      notifySelectionListeners(getActiveModel());
    }
  }
  
  private class MyModelChangeListener implements ArrangementRuleEditingModelImpl.Listener {

    @Override
    public void onChanged(@NotNull ArrangementRuleEditingModelImpl model, @NotNull TIntIntHashMap rowChanges) {
      onModelChange(model, rowChanges); 
    }
  }
  
  private static class MyModelNodesRefresher implements TObjectProcedure<ArrangementRuleEditingModelImpl> {
    @Override
    public boolean execute(ArrangementRuleEditingModelImpl model) {
      model.refreshTreeNodes(); 
      return true;
    }
  }
}
