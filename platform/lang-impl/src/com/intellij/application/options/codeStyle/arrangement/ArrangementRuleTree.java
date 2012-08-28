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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.StdArrangementRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementAtomMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementConditionsGrouper;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
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
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 8/10/12 2:10 PM
 */
public class ArrangementRuleTree {

  @NotNull private static final JLabel EMPTY_RENDERER         = new JLabel("");
  @NotNull private static final JLabel NEW_CONDITION_RENDERER = new JLabel("<html><b>&lt;empty rule&gt;</b>");
  @NotNull private static final Logger LOG                    = Logger.getInstance("#" + ArrangementRuleTree.class.getName());

  private static final int EMPTY_RULE_REMOVE_DELAY_MILLIS = 300;

  @NotNull private final List<ArrangementRuleSelectionListener> myListeners           = new ArrayList<ArrangementRuleSelectionListener>();
  @NotNull private final TreeSelectionModel                     mySelectionModel      = new MySelectionModel();
  @NotNull private final MyModelChangeListener                  myModelChangeListener = new MyModelChangeListener();
  @NotNull private final MyModelNodesRefresher                  myModelNodesRefresher = new MyModelNodesRefresher();
  @NotNull private final ArrangementRuleEditingModelBuilder     myModelBuilder        = new ArrangementRuleEditingModelBuilder();
  @NotNull private final Alarm                                  myAlarm               = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull private final RemoveInactiveNewModelRequest          myRequest             = new RemoveInactiveNewModelRequest();

  @NotNull private final TIntObjectHashMap<ArrangementNodeComponent>        myRenderers =
    new TIntObjectHashMap<ArrangementNodeComponent>();
  @NotNull private final TIntObjectHashMap<ArrangementRuleEditingModelImpl> myModels    =
    new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();

  @NotNull private final ArrangementTreeNode             myRoot;
  @NotNull private final DefaultTreeModel                myTreeModel;
  @NotNull private final Tree                            myTree;
  @NotNull private final ArrangementNodeComponentFactory myFactory;
  @NotNull private final ArrangementConditionsGrouper    myGrouper;

  private boolean myExplicitSelectionChange;
  private boolean mySkipSelectionChange;

  public ArrangementRuleTree(@NotNull List<StdArrangementRule> rules,
                             @NotNull ArrangementConditionsGrouper grouper,
                             @NotNull ArrangementNodeDisplayManager displayManager)
  {
    myGrouper = grouper;
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

      @NotNull
      @Override
      protected Condition<Integer> getWideSelectionBackgroundCondition() {
        return new Condition<Integer>() {
          @Override
          public boolean value(Integer row) {
            TreePath path = getPathForRow(row);
            if (path == null) {
              return false;
            }
            return isEmptyCondition(((ArrangementTreeNode)path.getLastPathComponent()).getBackingCondition());
          }
        };
      }

      @Override
      protected void processMouseEvent(MouseEvent e) {
        // JTree selects a node on mouse click at the same row (even outside the node bounds). We don't want to support
        // such selection because selected nodes are highlighted at the rule tree, so, it produces a 'blink' effect.
        if (e.getClickCount() > 0 && getNodeComponentAt(e.getX(), e.getY()) == null) {
          TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path == null || !isEmptyCondition(((ArrangementTreeNode)path.getLastPathComponent()).getBackingCondition())) {
            mySkipSelectionChange = true;
          }
        }
        try {
          super.processMouseEvent(e);
          if (mySkipSelectionChange) {
            notifySelectionListeners(getActiveModel());
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
    
    setRules(rules);
    
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
   * Updates renderer {@link ArrangementNodeComponent#setSelected(boolean) 'selected'} state on tree node selection change.
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
      ArrangementTreeNode node = (ArrangementTreeNode)p.getLastPathComponent();
      if (row < 0 && node != null) {
        row = node.getRow();
      }
      if (row < 0) {
        return;
      }
      ArrangementNodeComponent component = myRenderers.get(row);
      if (component != null) {
        component.setSelected(selected);
        myTreeModel.nodeChanged(node);
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

  private void map(@NotNull List<StdArrangementRule> rules) {
    for (StdArrangementRule rule : rules) {
      Pair<ArrangementRuleEditingModelImpl, TIntIntHashMap> pair = myModelBuilder.build(rule, myTree, myRoot, null, myGrouper);
      myModels.put(pair.first.getRow(), pair.first);
      pair.first.addListener(myModelChangeListener);
    }
  }

  public void addEditingListener(@NotNull ArrangementRuleSelectionListener listener) {
    myListeners.add(listener);
  }
  
  /**
   * @return    matcher model for the selected tree row(s) if any; null otherwise
   */
  @Nullable
  public ArrangementRuleEditingModelImpl getActiveModel() {
    TreePath[] paths = mySelectionModel.getSelectionPaths();
    if (paths == null) {
      return null;
    }
    
    // There is a possible case that particular settings node is represented on multiple rows and that non-leaf nodes are served
    // for more than one rule. No model is registered for them then and we want just to skip them.
    for (int i = paths.length - 1; i >= 0; i--) {
      int row = myTree.getRowForPath(paths[i]);
      ArrangementRuleEditingModelImpl model = myModels.get(row);
      if (model != null) {
        return model;
      }
    }
    return null;
  }

  /**
   * @return    UI component encapsulated by the current object. Subsequent calls to this method return the same reference all the time
   */
  @NotNull
  public Tree getTreeComponent() {
    return myTree;
  }

  /**
   * @return    rules configured at the current tree at the moment
   */
  @NotNull
  public List<StdArrangementRule> getRules() {
    int[] rows = myModels.keys();
    Arrays.sort(rows);
    List<StdArrangementRule> result = new ArrayList<StdArrangementRule>();
    for (int row : rows) {
      result.add(myModels.get(row).getRule());
    }
    return result;
  }

  public void setRules(@NotNull List<StdArrangementRule> rules) {
    myRenderers.clear();
    myModels.clear();
    while (myRoot.getChildCount() > 0)
    myTreeModel.removeNodeFromParent(myRoot.getFirstChild());
    map(rules);
    expandAll(myTree, new TreePath(myRoot));

    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info("Arrangement tree is refreshed. Given rules:");
      for (StdArrangementRule rule : rules) {
        LOG.info("  " + rule.toString());
      }
      LOG.info("Following models have been built:");
      myModels.forEachValue(new TObjectProcedure<ArrangementRuleEditingModelImpl>() {
        @Override
        public boolean execute(ArrangementRuleEditingModelImpl model) {
          LOG.info(String.format("  row %d, model '%s'", model.getRow(), model.getRule()));
          return true;
        }
      });
    }
  }
  
  @NotNull
  private ArrangementNodeComponent getNodeComponentAt(int row, @NotNull ArrangementMatchCondition condition) {
    ArrangementNodeComponent result = myRenderers.get(row);
    if (result == null || !result.getMatchCondition().equals(condition)) {
      myRenderers.put(row, result = myFactory.getComponent(condition));
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
    ArrangementNodeComponent component = getNodeComponentAt(e.getX(), e.getY());
    if (component == null) {
      return;
    }
    Rectangle changedScreenRectangle = component.handleMouseMove(e);
    if (changedScreenRectangle != null) {
      repaintScreenBounds(changedScreenRectangle);
    }
  }
  
  private void onMouseClicked(@NotNull MouseEvent e) {
    ArrangementNodeComponent component = getNodeComponentAt(e.getX(), e.getY());
    if (component != null) {
      component.handleMouseClick(e);
      return;
    }

    TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
    if (path != null && isEmptyCondition(((ArrangementTreeNode)path.getLastPathComponent()).getBackingCondition())) {
      return;
    }

    // Clear selection if it was a click at the empty space
    doClearSelection();
  }
  
  @Nullable
  private ArrangementNodeComponent getNodeComponentAt(int x, int y) {
    int row = myTree.getRowForLocation(x, y);
    return myRenderers.get(row);
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
    processRowChanges(rowChanges);

    // Perform necessary actions for the changed model.
    ArrangementTreeNode topMost = model.getTopMost();
    ArrangementTreeNode bottomMost = model.getBottomMost();
    doClearSelection();
    myExplicitSelectionChange = true;
    try {
      for (ArrangementTreeNode node = bottomMost; node != null; node = node.getParent()) {
        TreePath path = new TreePath(node.getPath());
        int row = myTree.getRowForPath(path);
        myRenderers.remove(row);
        mySelectionModel.addSelectionPath(path);
        myTreeModel.nodeChanged(node);
        mySelectionModel.addSelectionPath(path);
        ArrangementMatchCondition matchCondition = node.getBackingCondition();
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

  private void processRowChanges(TIntIntHashMap rowChanges) {
    expandAll(myTree, new TreePath(myTreeModel.getRoot()));

    // Refresh models.
    myModels.forEachValue(myModelNodesRefresher);

    // Shift row-based caches.
    final TIntObjectHashMap<ArrangementRuleEditingModelImpl> changedModelMappings =
      new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();
    rowChanges.forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(int oldRow, int newRow) {
        ArrangementRuleEditingModelImpl m = myModels.remove(oldRow);
        if (m != null) {
          changedModelMappings.put(newRow, m);
          m.setRow(newRow);
        }
        return true;
      }
    });
    putAll(changedModelMappings, myModels);

    // Drop JTree visual caches.
    rowChanges.forEachEntry(new TIntIntProcedure() {
      @Override
      public boolean execute(int oldRow, int newRow) {
        refreshTreeNode(oldRow);
        refreshTreeNode(newRow); 
        return true;
      }
      private void refreshTreeNode(int row) {
        TreePath path = myTree.getPathForRow(row);
        if (path == null) {
          return;
        }
        TreeNode node = (TreeNode)path.getLastPathComponent();
        if (node == null) {
          return;
        }
        myTreeModel.nodeStructureChanged(node);
      }
    });
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

  /**
   * Asks current model to create a new rule below the currently selected (or at the last position if no one is selected at the moment).
   * 
   * @return    model for the newly created row
   */
  @NotNull
  public ArrangementRuleEditingModel newModel() {
    ArrangementRuleEditingModelImpl activeModel = getActiveModel();
    final ArrangementTreeNode anchor = activeModel == null ? null : activeModel.getBottomMost();
    doClearSelection();
    Pair<ArrangementRuleEditingModelImpl,TIntIntHashMap> pair = myModelBuilder.build(
      ArrangementRuleEditingModel.EMPTY_RULE, myTree, myRoot, anchor, myGrouper
    );
    processRowChanges(pair.second);
    myModels.put(pair.first.getRow(), pair.first);
    pair.first.addListener(myModelChangeListener);
    mySelectionModel.setSelectionPath(myTree.getPathForRow(pair.first.getRow()));
    return pair.first;
  }

  private static boolean isEmptyCondition(@Nullable ArrangementMatchCondition condition) {
    return condition instanceof ArrangementCompositeMatchCondition
           && ((ArrangementCompositeMatchCondition)condition).getOperands().isEmpty();
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
      ArrangementMatchCondition node = ((ArrangementTreeNode)value).getBackingCondition();
      if (node == null) {
        return EMPTY_RENDERER;
      }
      else if (isEmptyCondition(node)) {
        return NEW_CONDITION_RENDERER;
      }
      
      if (row < 0) {
        return myFactory.getComponent(node).getUiComponent();
      }
      ArrangementNodeComponent component = getNodeComponentAt(row, node);
      component.setSelected(selected);
      return component.getUiComponent();
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
      
      myAlarm.cancelAllRequests();
      myAlarm.addRequest(myRequest, EMPTY_RULE_REMOVE_DELAY_MILLIS);
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
    
    private int mySelectedRowToRestore;
    
    @Override
    public void onChanged(@NotNull ArrangementRuleEditingModelImpl model, @NotNull TIntIntHashMap rowChanges) {
      onModelChange(model, rowChanges); 
    }

    @Override
    public void beforeModelDestroy(@NotNull ArrangementRuleEditingModelImpl model) {
      mySelectedRowToRestore = model.getRow();
      for (ArrangementTreeNode node = model.getBottomMost(); node != null; node = node.getParent()) {
        int row = myTree.getRowForPath(new TreePath(node.getPath()));
        myRenderers.remove(row);
        myModels.remove(row);
        if (node == model.getTopMost()) {
          break;
        }
      }
    }

    @Override
    public void afterModelDestroy(@NotNull TIntIntHashMap rowChanges) {
      processRowChanges(rowChanges);
      if (getActiveModel() != null) {
        return;
      }
      TreePath path = myTree.getPathForRow(mySelectedRowToRestore);
      if (path == null) {
        ArrangementTreeNode lastLeaf = myRoot.getLastLeaf();
        if (lastLeaf == null) {
          return;
        }
        path = new TreePath(lastLeaf.getPath());
      }
      mySelectionModel.setSelectionPath(path);
    }
  }
  
  private static class MyModelNodesRefresher implements TObjectProcedure<ArrangementRuleEditingModelImpl> {
    @Override
    public boolean execute(ArrangementRuleEditingModelImpl model) {
      model.refreshTreeNodes(); 
      return true;
    }
  }
  
  private class RemoveInactiveNewModelRequest implements Runnable {
    
    @SuppressWarnings("ConstantConditions")
    @Override
    public void run() {
      myAlarm.cancelAllRequests();
      Object[] values = myModels.getValues();
      ArrangementRuleEditingModelImpl activeModel = getActiveModel();
      boolean emptyRuleRemoved = false;
      for (Object value : values) {
        ArrangementRuleEditingModelImpl model = (ArrangementRuleEditingModelImpl)value;
        if (model != null && model != activeModel && model.getRule() == ArrangementRuleEditingModel.EMPTY_RULE) {
          model.destroy();
          emptyRuleRemoved = true;
        }
      }

      if (!emptyRuleRemoved || activeModel == null || getActiveModel() != null /* Selection was above the destroyed model */) {
        return;
      }

      for (Object value : myModels.getValues()) {
        ArrangementRuleEditingModelImpl model = (ArrangementRuleEditingModelImpl)value;
        if (activeModel.getRule().equals(model.getRule())) {
          mySelectionModel.setSelectionPath(new TreePath(activeModel.getBottomMost().getPath()));
        }
      }
    }
  }
}
