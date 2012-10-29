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

import com.intellij.application.options.codeStyle.arrangement.node.ArrangementEditableNode;
import com.intellij.application.options.codeStyle.arrangement.node.ArrangementRepresentationAwareNode;
import com.intellij.application.options.codeStyle.arrangement.node.ArrangementSectionNode;
import com.intellij.application.options.codeStyle.arrangement.node.match.ArrangementMatchNodeComponent;
import com.intellij.application.options.codeStyle.arrangement.node.match.ArrangementMatchNodeComponentFactory;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.codeStyle.arrangement.StdArrangementSettings;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementEntryMatcher;
import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementCompositeMatchCondition;
import com.intellij.psi.codeStyle.arrangement.model.ArrangementMatchCondition;
import com.intellij.psi.codeStyle.arrangement.settings.ArrangementStandardSettingsAware;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.Alarm;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.WideSelectionTreeUI;
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
import java.util.*;
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

  @NotNull private final List<ArrangementRuleSelectionListener> myListeners            = new ArrayList<ArrangementRuleSelectionListener>();
  @NotNull private final MySelectionModel                       mySelectionModel       = new MySelectionModel();
  @NotNull private final MyModelChangeListener                  myModelChangeListener  = new MyModelChangeListener();
  @NotNull private final MyModelNodesRefresher                  myModelNodesRefresher  = new MyModelNodesRefresher();
  @NotNull private final ArrangementRuleEditingModelBuilder     myModelBuilder         = new ArrangementRuleEditingModelBuilder();
  @NotNull private final Alarm                                  myAlarm                = new Alarm(Alarm.ThreadToUse.SWING_THREAD);
  @NotNull private final RemoveInactiveNewModelRequest          myRequest              = new RemoveInactiveNewModelRequest();
  @NotNull private final ArrangementGroupingRulesManager        myGroupingRulesManager = ArrangementGroupingRulesManager.INSTANCE;

  @NotNull private final TIntObjectHashMap<ArrangementMatchNodeComponent>   myRenderers =
    new TIntObjectHashMap<ArrangementMatchNodeComponent>();
  @NotNull private final TIntObjectHashMap<ArrangementRuleEditingModelImpl> myModels    =
    new TIntObjectHashMap<ArrangementRuleEditingModelImpl>();

  @NotNull private final ArrangementTreeNode                  myMatchRulesRoot;
  @NotNull private final DefaultTreeModel                     myTreeModel;
  @NotNull private final Tree                                 myTree;
  @NotNull private final ArrangementMatchNodeComponentFactory myFactory;
  @NotNull private final List<Set<ArrangementMatchCondition>> myUiGroupingRules;
  @NotNull private final ArrangementColorsProvider            myColorsProvider;

  @Nullable private final MutableTreeNode myGroupingsRoot;

  private int myRowUnderMouse = -1;

  private int     myCanvasWidth;
  private boolean myExplicitSelectionChange;
  private boolean mySkipSelectionChange;

  public ArrangementRuleTree(@Nullable StdArrangementSettings settings,
                             @NotNull List<Set<ArrangementMatchCondition>> uiGroupingRules,
                             @NotNull ArrangementNodeDisplayManager displayManager,
                             @NotNull ArrangementColorsProvider colorsProvider,
                             @NotNull ArrangementStandardSettingsAware settingsFilter)
  {
    myUiGroupingRules = uiGroupingRules;
    myColorsProvider = colorsProvider;
    myFactory = new ArrangementMatchNodeComponentFactory(displayManager, colorsProvider, new Runnable() {
      @Override
      public void run() {
        notifySelectionListeners(); 
      }
    }, uiGroupingRules);
    myGroupingsRoot = myGroupingRulesManager.buildAvailableRules(settingsFilter, displayManager);
    ArrangementTreeNode root = new ArrangementTreeNode(null);
    if (myGroupingsRoot != null) {
      root.add(myGroupingsRoot);
    }
    myMatchRulesRoot = new ArrangementSectionNode(ApplicationBundle.message("arrangement.settings.section.match"));
    root.add(myMatchRulesRoot);
    myTreeModel = new DefaultTreeModel(root);

    final Condition<Integer> wideSelectionCondition = new Condition<Integer>() {
      @Override
      public boolean value(Integer row) {
        TreePath path = myTree.getPathForRow(row);
        if (path == null) {
          return false;
        }
        return isEmptyCondition(((ArrangementTreeNode)path.getLastPathComponent()).getBackingCondition());
      }
    };
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
        return wideSelectionCondition;
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
          List<ArrangementRuleEditingModelImpl> activeModels = getActiveModels();
          if (mySkipSelectionChange && activeModels.isEmpty()) {
            notifySelectionListeners();
          }
        }
        finally {
          mySkipSelectionChange = false;
        }
      }
    };
    myTree.setSelectionModel(mySelectionModel);
    myTree.setRootVisible(false);
    myTree.setRowHeight(0); // Don't insist on the same row height
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
      public void mouseEntered(MouseEvent e) {
        onMouseEntered(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        onMouseExited();
      }

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
    
    setSettings(settings);
    
    myTree.setShowsRootHandles(false);
    MyCellRenderer renderer = new MyCellRenderer();
    myTree.setCellRenderer(renderer);
    myTree.setEditable(true);
    myTree.setCellEditor(new MyCellEditor(myTree, renderer));
    
    // IJ wide selection tree ui is not applied for some LAFs (e.g. GTK) and default wide selection tree ui doesn't allow
    // to customize selection background appliance algorithm (don't draw background for rule components; do draw for
    // 'new rule' node).
    // That's why we forcibly apply UI with custom selection strategy here.
    // P.S. 'wide selection tree ui' appliance decision is taken at the com.intellij.ui.treeStructure.Tree.setUI().
    myTree.setUI(new WideSelectionTreeUI(true, wideSelectionCondition) {
      @Override
      protected boolean shouldPaintExpandControl(TreePath path, int row, boolean isExpanded, boolean hasBeenExpanded, boolean isLeaf) {
        return false;
      }

      @Override
      protected void paintSelectedRows(Graphics g, JTree tr) {
        if (myRowUnderMouse < 0) {
          super.paintSelectedRows(g, tr);
          return;
        }
        final Rectangle rect = tr.getVisibleRect();
        final int firstVisibleRow = tr.getClosestRowForLocation(rect.x, rect.y);
        final int lastVisibleRow = tr.getClosestRowForLocation(rect.x, rect.y + rect.height);

        for (int row = firstVisibleRow; row <= lastVisibleRow; row++) {
          Color color = null;
          if (tr.getSelectionModel().isRowSelected(row) && wideSelectionCondition.value(row)) {
            color = UIUtil.getTreeSelectionBackground(tr.hasFocus());
          }
          else if (row == myRowUnderMouse) {
            color = myColorsProvider.getRowUnderMouseBackground();
          }

          if (color == null) {
            color = myTree.getBackground();
          }

          final Rectangle bounds = tr.getRowBounds(row);
          g.setColor(color);
          g.fillRect(0, bounds.y, tr.getWidth(), bounds.height);
        }
      }
    });
  }

  public void updateCanvasWidth(final int width) {
    myCanvasWidth = width;
    myRenderers.forEachKey(new TIntProcedure() {
      @Override
      public boolean execute(int row) {
        doUpdateCanvasWidth(row); 
        return true;
      }
    });
  }

  private void doUpdateCanvasWidth(int row) {
    if (myCanvasWidth <= 0) {
      return;
    }
    ArrangementMatchNodeComponent component = myRenderers.get(row);
    if (component == null) {
      return;
    }
    if (!component.onCanvasWidthChange(myCanvasWidth - myTree.getRowBounds(row).x)) {
      return;
    }

    TreePath path = myTree.getPathForRow(row);
    if (path != null) {
      myTreeModel.nodeChanged((TreeNode)path.getLastPathComponent());
    }
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
      if (myMatchRulesRoot.getChildCount() > 0) {
        mySelectionModel.setSelectionPath(new TreePath(myMatchRulesRoot.getFirstChild().getPath()));
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
    myRenderers.forEachValue(new TObjectProcedure<ArrangementMatchNodeComponent>() {
      @Override
      public boolean execute(ArrangementMatchNodeComponent node) {
        node.setSelected(false);
        return true;
      }
    });
    myTree.repaint();
  }

  /**
   * Updates renderer {@link ArrangementMatchNodeComponent#setSelected(boolean) 'selected'} state on tree node selection change.
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
      ArrangementMatchNodeComponent component = myRenderers.get(row);
      if (component != null) {
        component.setSelected(selected);
        myTreeModel.nodeChanged(node);
        repaintComponent(component, false);
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

  private void map(@NotNull List<StdArrangementMatchRule> rules) {
    for (StdArrangementMatchRule rule : rules) {
      Pair<ArrangementRuleEditingModelImpl, TIntIntHashMap> pair
        = myModelBuilder.build(rule, myTree, myMatchRulesRoot, null, myUiGroupingRules);
      if (pair != null && pair.first != null) {
        myModels.put(pair.first.getRow(), pair.first);
        pair.first.addListener(myModelChangeListener);
      }
    }
  }

  public void addEditingListener(@NotNull ArrangementRuleSelectionListener listener) {
    myListeners.add(listener);
  }
  
  /**
   * @return    matcher model for the selected tree row(s) if any; null otherwise
   */
  @NotNull
  public List<ArrangementRuleEditingModelImpl> getActiveModels() {
    TreePath[] paths = mySelectionModel.getSelectionPaths();
    if (paths == null) {
      return Collections.emptyList();
    }
    
    // There is a possible case that particular settings node is represented on multiple rows and that non-leaf nodes are served
    // for more than one rule. No model is registered for them then and we want just to skip them.
    List<ArrangementRuleEditingModelImpl> result = new ArrayList<ArrangementRuleEditingModelImpl>();
    Set<ArrangementRuleEditingModelImpl> set = new HashSet<ArrangementRuleEditingModelImpl>();
    for (int i = paths.length - 1; i >= 0; i--) {
      int row = myTree.getRowForPath(paths[i]);
      ArrangementRuleEditingModelImpl model = myModels.get(row);
      if (model != null && !set.contains(model)) {
        result.add(model);
        set.add(model);
      }
    }
    return result;
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
  public StdArrangementSettings getSettings() {
    int[] rows = myModels.keys();
    Arrays.sort(rows);
    List<StdArrangementMatchRule> rules = new ArrayList<StdArrangementMatchRule>();
    ArrangementMatchCondition prevGroup = null;
    Set<ArrangementMatchCondition> implicitGroupConditions = new HashSet<ArrangementMatchCondition>();
    for (int row : rows) {
      ArrangementRuleEditingModelImpl model = myModels.get(row);
      ArrangementTreeNode topMost = model.getTopMost();
      ArrangementMatchCondition currentGroup = topMost.getBackingCondition();
      if (prevGroup != null && !prevGroup.equals(currentGroup)) {
        rules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(prevGroup)));
        implicitGroupConditions.add(prevGroup);
        prevGroup = null;
      }
      if (!myMatchRulesRoot.equals(topMost) && !topMost.equals(model.getBottomMost()) && !implicitGroupConditions.contains(currentGroup)) {
        prevGroup = currentGroup;
      }
      rules.add(model.getRule());
    }

    if (prevGroup != null) {
      rules.add(new StdArrangementMatchRule(new StdArrangementEntryMatcher(prevGroup)));
    }

    if (myGroupingsRoot == null) {
      return new StdArrangementSettings(rules);
    }
    else {
      return new StdArrangementSettings(myGroupingRulesManager.buildRules(myGroupingsRoot), rules);
    }
  }

  public void setSettings(@Nullable StdArrangementSettings settings) {
    myRenderers.clear();
    myModels.clear();
    while (myMatchRulesRoot.getChildCount() > 0)
    myTreeModel.removeNodeFromParent(myMatchRulesRoot.getFirstChild());
    if (settings == null) {
      return;
    }

    if (myGroupingsRoot != null) {
      myGroupingRulesManager.applyRules(settings.getGroupings(), myGroupingsRoot, myTreeModel);
    }

    List<StdArrangementMatchRule> rules = settings.getRules();
    map(rules);
    expandAll(myTree, new TreePath(myTreeModel.getRoot()));

    if (ArrangementConstants.LOG_RULE_MODIFICATION) {
      LOG.info("Arrangement tree is refreshed. Given rules:");
      for (StdArrangementMatchRule rule : rules) {
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
  private ArrangementMatchNodeComponent getNodeComponentAt(int row,
                                                      @NotNull ArrangementMatchCondition condition,
                                                      @Nullable ArrangementRuleEditingModelImpl model)
  {
    ArrangementMatchNodeComponent result = myRenderers.get(row);
    if (result == null || !result.getMatchCondition().equals(condition)) {
      myRenderers.put(row, result = myFactory.getComponent(condition, model, true));
      doUpdateCanvasWidth(row);
    }
    return result;
  }

  private void onMouseMoved(@NotNull MouseEvent e) {
    int row = myTree.getRowForLocation(e.getX(), e.getY());
    if (row < 0 || row != myRowUnderMouse) {
      onMouseExited();
    }
    ArrangementMatchNodeComponent component = myRenderers.get(row);
    if (component == null) {
      return;
    }
    
    onMouseEntered(e);
    Rectangle changedScreenRectangle = component.handleMouseMove(e);
    if (changedScreenRectangle != null) {
      repaintScreenBounds(changedScreenRectangle, false);
    }
  }

  private void onMouseEntered(@NotNull MouseEvent e) {
    int row = myTree.getRowForLocation(e.getX(), e.getY());
    if (row < 0) {
      return;
    }
    
    myRowUnderMouse = row;
    ArrangementMatchNodeComponent component = myRenderers.get(row);
    if (component == null) {
      return;
    }
    repaintComponent(component, true);
  }

  private void onMouseExited() {
    if (myRowUnderMouse < 0) {
      return;
    }

    ArrangementMatchNodeComponent component = myRenderers.get(myRowUnderMouse);
    myRowUnderMouse = -1;
    if (component != null) {
      repaintComponent(component, true);
    }
  }

  private void onMouseClicked(@NotNull MouseEvent e) {
    ArrangementMatchNodeComponent component = getNodeComponentAt(e.getX(), e.getY());
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
  private ArrangementMatchNodeComponent getNodeComponentAt(int x, int y) {
    int row = myTree.getRowForLocation(x, y);
    return myRenderers.get(row);
  }

  private void repaintComponent(@NotNull ArrangementMatchNodeComponent component, boolean fromLeftEdge) {
    Rectangle bounds = component.getScreenBounds();
    if (bounds != null) {
      repaintScreenBounds(bounds, fromLeftEdge);
    }
  }

  private void repaintScreenBounds(@NotNull Rectangle bounds, boolean fromLeftEdge) {
    Point location = bounds.getLocation();
    SwingUtilities.convertPointFromScreen(location, myTree);
    int x = location.x;
    int width = bounds.width;
    if (fromLeftEdge) {
      x = 0;
      width += location.x;
    }
    myTree.repaint(x, location.y, width, bounds.height);
  }

  private void notifySelectionListeners() {
    List<ArrangementRuleEditingModelImpl> models = getActiveModels();
    for (ArrangementRuleSelectionListener listener : myListeners) {
      listener.onSelectionChange(models);
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
        myTreeModel.nodeChanged(node);
        if (node == bottomMost) {
          mySelectionModel.addSelectionPath(path);
        }
        ArrangementMatchCondition matchCondition = node.getBackingCondition();
        if (matchCondition != null) {
          getNodeComponentAt(row, matchCondition, model).setSelected(true);
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
    
    expandAll(myTree, new TreePath(myTreeModel.getRoot()));

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
    List<ArrangementRuleEditingModelImpl> activeModels = getActiveModels();
    final ArrangementTreeNode anchor = activeModels.size() != 1 ? null : activeModels.get(0).getBottomMost();
    doClearSelection();
    Pair<ArrangementRuleEditingModelImpl,TIntIntHashMap> pair = myModelBuilder.build(
      ArrangementRuleEditingModel.EMPTY_RULE, myTree, myMatchRulesRoot, anchor, myUiGroupingRules
    );
    assert pair != null;
    assert pair.first != null;
    assert pair.second != null;
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

  @SuppressWarnings("MethodMayBeStatic")
  public void disposeUI() {
    Container parent = EMPTY_RENDERER.getParent();
    if (parent != null) parent.remove(EMPTY_RENDERER);
  }

  private class MyCellRenderer extends DefaultTreeCellRenderer {
    @Override
    public Component getTreeCellRendererComponent(JTree tree,
                                                  Object value,
                                                  boolean selected,
                                                  boolean expanded,
                                                  boolean leaf,
                                                  int row,
                                                  boolean hasFocus) {
      if (value instanceof ArrangementRepresentationAwareNode) {
        return ((ArrangementRepresentationAwareNode)value).getRenderer();
      }

      ArrangementMatchCondition node = ((ArrangementTreeNode)value).getBackingCondition();
      if (node == null) {
        return EMPTY_RENDERER;
      }
      if (isEmptyCondition(node)) {
        return NEW_CONDITION_RENDERER;
      }

      if (row < 0) {
        ArrangementMatchNodeComponent component = myFactory.getComponent(node, null, true);
        doUpdateCanvasWidth(row);
        return component.getUiComponent();
      }
      ArrangementMatchNodeComponent component = getNodeComponentAt(row, node, myModels.get(row));
      component.setSelected(selected);
      return component.getUiComponent();
    }
  }
  
  private static class MyCellEditor extends DefaultTreeCellEditor {
    MyCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
      super(tree, renderer);
    }

    @Override
    public Component getTreeCellEditorComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row) {
      if (value instanceof ArrangementEditableNode) {
        return ((ArrangementEditableNode)value).getEditor();
      }
      else {
        return super.getTreeCellEditorComponent(tree, value, isSelected, expanded, leaf, row);
      }
    }

    @Override
    public boolean isCellEditable(EventObject event) {
      if (event instanceof MouseEvent) {
        MouseEvent mouseEvent = (MouseEvent)event;
        TreePath path = tree.getPathForLocation(mouseEvent.getX(), mouseEvent.getY());
        if (path == null) {
          return false;
        }
        return path.getLastPathComponent() instanceof ArrangementEditableNode;
      }
      return false;
    }
  }
  
  private class MySelectionModel extends DefaultTreeSelectionModel {

    MySelectionModel() {
      setSelectionMode(CONTIGUOUS_TREE_SELECTION);
    }

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
      ArrangementTreeNode node = (ArrangementTreeNode)path.getLastPathComponent();

      super.addSelectionPath(path);
      
      if (node.getChildCount() > 0) {
        // Select the whole section.
        for (ArrangementTreeNode child = node.getFirstChild(), last = node.getLastChild(); child != null; child = child.getNextNode()) {
          addSelectionPath(new TreePath(child.getPath()));
          if (child == last) {
            break;
          }
        }
      }
      
      notifySelectionListeners();
    }

    public boolean isRowSelected(int row) {
      for (int i : getSelectionRows()) {
        if (i == row) {
          return true;
        }
      }
      return false;
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
      mySelectedRowToRestore = myTree.getRowForPath(new TreePath(model.getBottomMost().getPath()));
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
      if (!getActiveModels().isEmpty()) {
        return;
      }
      TreePath path = myTree.getPathForRow(mySelectedRowToRestore);
      if (path == null) {
        ArrangementTreeNode lastLeaf = myMatchRulesRoot.getLastLeaf();
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
      Set<ArrangementRuleEditingModelImpl> activeModels = ContainerUtil.newHashSet(getActiveModels());
      boolean emptyRuleRemoved = false;
      for (Object value : values) {
        ArrangementRuleEditingModelImpl model = (ArrangementRuleEditingModelImpl)value;
        if (model != null && !activeModels.contains(model) && model.getRule() == ArrangementRuleEditingModel.EMPTY_RULE) {
          model.destroy();
          emptyRuleRemoved = true;
        }
      }

      if (!emptyRuleRemoved || activeModels.isEmpty() || !getActiveModels().isEmpty() /* Selection was above the destroyed model */) {
        return;
      }
      
      doClearSelection();
      for (Object value : myModels.getValues()) {
        ArrangementRuleEditingModelImpl model = (ArrangementRuleEditingModelImpl)value;
        if (activeModels.contains(model)) {
          mySelectionModel.addSelectionPath(new TreePath(model.getBottomMost().getPath()));
        }
      }
    }
  }
}
