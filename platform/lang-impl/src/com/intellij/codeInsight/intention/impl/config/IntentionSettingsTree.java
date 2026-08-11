// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.internal.inspector.PropertyBean;
import com.intellij.internal.inspector.UiInspectorTreeRendererContextProvider;
import com.intellij.internal.inspector.UiInspectorUtil;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApiStatus.Internal
abstract class IntentionSettingsTree {
  private JComponent myComponent;
  private CheckboxTree myTree;
  private FilterComponent myFilter;

  private final Map<IntentionActionMetaData, Boolean> myIntentionToCheckStatus = new HashMap<>();
  private JPanel myNorthPanel;

  protected IntentionSettingsTree() {
    initTree();
  }

  public JTree getTree() {
    return myTree;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private void initTree() {
    myTree = new CheckboxTree(new IntentionsTreeCellRenderer(), new IntentionTreeNode(null)) {
      @Override
      protected void onDoubleClick(@Nullable CheckedTreeNode node) {
        if (node == null) return;
        if (node.getChildCount() == 0) {
          node.setChecked(!node.isChecked());
        }
        else {
          TreePath path = new TreePath(node.getPath());
          if (myTree.isExpanded(path)) {
            myTree.collapsePath(path);
          }
          else {
            myTree.expandPath(path);
          }
        }
        myTree.repaint();
      }

      @Override
      public @Nullable TreePath getPathForLocation(int x, int y) {
        TreePath path = getClosestPathForLocation(x, y);
        if (path == null) return null;
        Rectangle pathBounds = getPathBounds(path);
        return pathBounds != null && y >= pathBounds.y && y < (pathBounds.y + pathBounds.height) ? path : null;
      }
    };

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getNewLeadSelectionPath();
        Object userObject = path == null ? null : ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
        selectionChanged(userObject);
      }
    });

    myFilter = new MyFilterComponent();
    myComponent = new JPanel(new BorderLayout());
    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myTree);
    myNorthPanel = new JPanel(new BorderLayout());
    myNorthPanel.add(myFilter, BorderLayout.CENTER);
    myNorthPanel.setBorder(JBUI.Borders.emptyBottom(2));

    DefaultActionGroup group = new DefaultActionGroup();
    CommonActionsManager actionManager = CommonActionsManager.getInstance();

    TreeExpander treeExpander = new DefaultTreeExpander(myTree);
    group.add(actionManager.createExpandAllAction(treeExpander, myTree));
    group.add(actionManager.createCollapseAllAction(treeExpander, myTree));

    ActionToolbar treeToolbar = ActionManager.getInstance().createActionToolbar("IntentionSettingsTree", group, true);
    treeToolbar.setTargetComponent(myTree);
    myNorthPanel.add(treeToolbar.getComponent(), BorderLayout.WEST);

    myComponent.add(myNorthPanel, BorderLayout.NORTH);
    myComponent.add(scrollPane, BorderLayout.CENTER);

    myFilter.reset();
  }

  protected abstract void selectionChanged(Object selected);

  protected abstract Collection<IntentionActionMetaData> filterModel(String filter, boolean force);

  public void filter(@NotNull Collection<IntentionActionMetaData> intentionsToShow) {
    refreshCheckStatus((IntentionTreeNode)myTree.getModel().getRoot());
    reset(copyAndSort(intentionsToShow));
  }

  public void reset() {
    IntentionManagerImpl intentionManager = (IntentionManagerImpl)IntentionManager.getInstance();
    while (intentionManager.hasActiveRequests()) {
      TimeoutUtil.sleep(100);
    }

    IntentionManagerSettings intentionManagerSettings = IntentionManagerSettings.getInstance();
    myIntentionToCheckStatus.clear();
    Collection<@NotNull IntentionActionMetaData> intentions = intentionManagerSettings.getMetaData();
    for (IntentionActionMetaData metaData : intentions) {
      myIntentionToCheckStatus.put(metaData, intentionManagerSettings.isEnabled(metaData));
    }
    reset(copyAndSort(intentions));
  }

  private void reset(@NotNull List<IntentionActionMetaData> sortedIntentions) {
    IntentionTreeNode root = new IntentionTreeNode(null);
    DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
    for (IntentionActionMetaData metaData : sortedIntentions) {
      IntentionTreeNode node = root;
      for (String name : metaData.myCategory) {
        IntentionTreeNode child = findChild(node, name);
        if (child == null) {
          IntentionTreeNode newChild = new IntentionTreeNode(name);
          node.insert(newChild, node.getChildCount());
          child = newChild;
        }
        node = child;
      }
      node.insert(new IntentionTreeNode(metaData), node.getChildCount());
    }
    resetCheckMark(root);
    treeModel.setRoot(root);
    TreeUtil.expandAll(myTree);
    TreeUtil.selectRow(myTree, 0);
  }

  public void selectIntention(String familyName) {
    IntentionTreeNode child = findChildRecursively(getRoot(), familyName);
    if (child != null) {
      TreeUtil.selectPath(myTree, new TreePath(child.getPath()));
    }
  }

  private static @NotNull List<IntentionActionMetaData> copyAndSort(@NotNull Collection<IntentionActionMetaData> intentionsToShow) {
    List<IntentionActionMetaData> copy = new ArrayList<>(intentionsToShow);
    copy.sort((data1, data2) -> {
      int result = ArrayUtil.lexicographicCompare(data1.myCategory, data2.myCategory);
      return result != 0 ? result : data1.getFamily().compareTo(data2.getFamily());
    });
    return copy;
  }

  private IntentionTreeNode getRoot() {
    return (IntentionTreeNode)myTree.getModel().getRoot();
  }

  private boolean resetCheckMark(IntentionTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData metaData) {
      boolean enabled = myIntentionToCheckStatus.get(metaData) == Boolean.TRUE;
      root.setChecked(enabled);
      return enabled;
    }
    else {
      root.setChecked(false);
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(IntentionTreeNode node) {
          if (resetCheckMark(node)) {
            root.setChecked(true);
          }
        }
      });
      return root.isChecked();
    }
  }

  private static IntentionTreeNode findChild(TreeNode node, String name) {
    Ref<IntentionTreeNode> found = new Ref<>();
    visitChildren(node, new CheckedNodeVisitor() {
      @Override
      public void visit(IntentionTreeNode node) {
        if (name.equals(getNodeText(node, true))) {
          found.set(node);
        }
      }
    });
    return found.get();
  }

  private static IntentionTreeNode findChildRecursively(TreeNode node, String name) {
    Ref<IntentionTreeNode> found = new Ref<>();
    visitChildren(node, new CheckedNodeVisitor() {
      @Override
      public void visit(IntentionTreeNode node) {
        if (found.get() != null) return;
        if (node.getUserObject() instanceof IntentionActionMetaData) {
          if (name.equals(getNodeText(node, true))) {
            found.set(node);
          }
        }
        else {
          IntentionTreeNode child = findChildRecursively(node, name);
          if (child != null) {
            found.set(child);
          }
        }
      }
    });
    return found.get();
  }

  private static String getNodeText(IntentionTreeNode node, boolean full) {
    Object userObject = node.getUserObject();
    if (userObject instanceof String text) {
      return text;
    }
    else if (userObject instanceof IntentionActionMetaData metaData) {
      if (full && metaData.getAction() instanceof IntentionActionWrapper wrapper) {
        return wrapper.getFullFamilyName();
      }
      return metaData.getFamily();
    }
    else {
      return "???";
    }
  }

  public void apply() {
    apply(getRoot());
  }

  private void refreshCheckStatus(IntentionTreeNode  root) {
    if (root.getUserObject() instanceof IntentionActionMetaData actionMetaData) {
      myIntentionToCheckStatus.put(actionMetaData, root.isChecked());
    }
    else {
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(IntentionTreeNode node) {
          refreshCheckStatus(node);
        }
      });
    }
  }

  private static void apply(IntentionTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData actionMetaData) {
      IntentionManagerSettings.getInstance().setEnabled(actionMetaData, root.isChecked());
    }
    else {
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(IntentionTreeNode node) {
          apply(node);
        }
      });
    }
  }

  public boolean isModified() {
    return isModified(getRoot());
  }

  private static boolean isModified(IntentionTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData actionMetaData) {
      boolean enabled = IntentionManagerSettings.getInstance().isEnabled(actionMetaData);
      return enabled != root.isChecked();
    }
    else {
      boolean[] modified = new boolean[]{false};
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(IntentionTreeNode node) {
          modified[0] |= isModified(node);
        }
      });
      return modified[0];
    }
  }

  public void dispose() {
    myFilter.dispose();
  }

  public void setFilter(String filter) {
    myFilter.setFilter(filter);
  }

  public String getFilter() {
    return myFilter.getFilter();
  }

  interface CheckedNodeVisitor {
    void visit(IntentionTreeNode node);
  }

  private static void visitChildren(TreeNode node, CheckedNodeVisitor visitor) {
    Enumeration<?> children = node.children();
    while (children.hasMoreElements()) {
      IntentionTreeNode child = (IntentionTreeNode)children.nextElement();
      visitor.visit(child);
    }
  }

  private final class MyFilterComponent extends FilterComponent {
    private final TreeExpansionMonitor<DefaultMutableTreeNode> myExpansionMonitor = TreeExpansionMonitor.install(myTree);
    private String previousFilter = null;

    MyFilterComponent() {
      super("INTENTION_FILTER_HISTORY", 10);
    }

    @Override
    public void filter() {
      onlineFilter();
      IdeFocusManager.getGlobalInstance().requestFocus(myTree, true);
    }

    @Override
    protected void onlineFilter() {
      String filter = getFilter();
      if (Objects.equals(filter, previousFilter)) {
        return;
      }
      previousFilter = filter;
      if (filter != null && !filter.isEmpty() || !myExpansionMonitor.isFreeze()) {
        myExpansionMonitor.freeze();
      }
      IntentionSettingsTree.this.filter(filterModel(filter, true));
      TreeUtil.expandAll(myTree);
      if (filter == null || filter.isEmpty()) {
        TreeUtil.collapseAll(myTree, 1);
        myExpansionMonitor.restore();
      }
      if (myTree.getSelectionRows() == null) {
        TreeUtil.selectRow(myTree, 0);
      }
    }
  }

  public JPanel getToolbarPanel() {
    return myNorthPanel;
  }

  private final class IntentionsTreeCellRenderer extends CheckboxTree.CheckboxTreeCellRenderer implements UiInspectorTreeRendererContextProvider {
    IntentionsTreeCellRenderer() {
      super(true);
    }

    @Override
    public void customizeRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
      if (!(value instanceof IntentionTreeNode node)) {
        return;
      }

      SimpleTextAttributes attributes = node.getUserObject() instanceof IntentionActionMetaData
                                        ? SimpleTextAttributes.REGULAR_ATTRIBUTES
                                        : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      Color background = UIUtil.getTreeBackground(selected, true);
      UIUtil.changeBackGround(this, background);
      SearchUtil.appendFragments(myFilter != null ? myFilter.getFilter() : null,
                                 getNodeText(node, false),
                                 attributes.getStyle(),
                                 attributes.getFgColor(),
                                 background,
                                 getTextRenderer());
    }

    @Override
    public @NotNull List<PropertyBean> getUiInspectorContext(@NotNull JTree tree, @Nullable Object value, int row) {
      if (value instanceof IntentionTreeNode node && node.getUserObject() instanceof IntentionActionMetaData metaData) {
        List<PropertyBean> result = new ArrayList<>();
        result.add(new PropertyBean("Intention Class",
                                    UiInspectorUtil.getClassPresentation(IntentionActionDelegate.unwrap(metaData.getAction())), true));
        result.add(new PropertyBean("Intention description directory", metaData.getDescriptionDirectoryName(), true));
        return result;
      }
      return Collections.emptyList();
    }
  }
}
