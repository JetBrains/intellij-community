// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.List;
import java.util.*;

public abstract class IntentionSettingsTree {
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
    myTree = new CheckboxTree(new IntentionsTreeCellRenderer(), new CheckedTreeNode(null));

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        Object userObject = ((DefaultMutableTreeNode)path.getLastPathComponent()).getUserObject();
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
    refreshCheckStatus((CheckedTreeNode)myTree.getModel().getRoot());
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
    CheckedTreeNode root = new CheckedTreeNode(null);
    DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
    for (IntentionActionMetaData metaData : sortedIntentions) {
      CheckedTreeNode node = root;
      for (String name : metaData.myCategory) {
        CheckedTreeNode child = findChild(node, name);
        if (child == null) {
          CheckedTreeNode newChild = new CheckedTreeNode(name);
          treeModel.insertNodeInto(newChild, node, node.getChildCount());
          child = newChild;
        }
        node = child;
      }
      treeModel.insertNodeInto(new CheckedTreeNode(metaData), node, node.getChildCount());
    }
    resetCheckMark(root);
    treeModel.setRoot(root);
    treeModel.nodeChanged(root);
    TreeUtil.expandAll(myTree);
    myTree.setSelectionRow(0);
  }

  public void selectIntention(String familyName) {
    CheckedTreeNode child = findChildRecursively(getRoot(), familyName);
    if (child != null) {
      TreePath path = new TreePath(child.getPath());
      TreeUtil.selectPath(myTree, path);
    }
  }

  private static @NotNull List<IntentionActionMetaData> copyAndSort(@NotNull Collection<IntentionActionMetaData> intentionsToShow) {
    List<IntentionActionMetaData> copy = new ArrayList<>(intentionsToShow);
    copy.sort((data1, data2) -> {
      String[] category1 = data1.myCategory;
      String[] category2 = data2.myCategory;
      int result = ArrayUtil.lexicographicCompare(category1, category2);
      if (result != 0) {
        return result;
      }
      return data1.getFamily().compareTo(data2.getFamily());
    });
    return copy;
  }

  private CheckedTreeNode getRoot() {
    return (CheckedTreeNode)myTree.getModel().getRoot();
  }

  private boolean resetCheckMark(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData metaData) {
      Boolean b = myIntentionToCheckStatus.get(metaData);
      boolean enabled = b == Boolean.TRUE;
      root.setChecked(enabled);
      return enabled;
    }
    else {
      root.setChecked(false);
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(CheckedTreeNode node) {
          if (resetCheckMark(node)) {
            root.setChecked(true);
          }
        }
      });
      return root.isChecked();
    }
  }

  private static CheckedTreeNode findChild(TreeNode node, String name) {
    Ref<CheckedTreeNode> found = new Ref<>();
    visitChildren(node, new CheckedNodeVisitor() {
      @Override
      public void visit(CheckedTreeNode node) {
        String text = getNodeText(node, true);
        if (name.equals(text)) {
          found.set(node);
        }
      }
    });
    return found.get();
  }

  private static CheckedTreeNode findChildRecursively(TreeNode node, String name) {
    Ref<CheckedTreeNode> found = new Ref<>();
    visitChildren(node, new CheckedNodeVisitor() {
      @Override
      public void visit(CheckedTreeNode node) {
        if (found.get() != null) return;
        Object userObject = node.getUserObject();
        if (userObject instanceof IntentionActionMetaData) {
          String text = getNodeText(node, true);
          if (name.equals(text)) {
            found.set(node);
          }
        }
        else {
          CheckedTreeNode child = findChildRecursively(node, name);
          if (child != null) {
            found.set(child);
          }
        }
      }
    });
    return found.get();
  }

  private static String getNodeText(CheckedTreeNode node, boolean full) {
    Object userObject = node.getUserObject();
    if (userObject instanceof String) {
      return (String)userObject;
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
    CheckedTreeNode root = getRoot();
    apply(root);
  }

  private void refreshCheckStatus(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData actionMetaData) {
      myIntentionToCheckStatus.put(actionMetaData, root.isChecked());
    }
    else {
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(CheckedTreeNode node) {
          refreshCheckStatus(node);
        }
      });
    }
  }

  private static void apply(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData actionMetaData) {
      IntentionManagerSettings.getInstance().setEnabled(actionMetaData, root.isChecked());
    }
    else {
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(CheckedTreeNode node) {
          apply(node);
        }
      });
    }
  }

  public boolean isModified() {
    return isModified(getRoot());
  }

  private static boolean isModified(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData actionMetaData) {
      boolean enabled = IntentionManagerSettings.getInstance().isEnabled(actionMetaData);
      return enabled != root.isChecked();
    }
    else {
      boolean[] modified = new boolean[]{false};
      visitChildren(root, new CheckedNodeVisitor() {
        @Override
        public void visit(CheckedTreeNode node) {
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
    void visit(CheckedTreeNode node);
  }

  private static void visitChildren(TreeNode node, CheckedNodeVisitor visitor) {
    Enumeration<?> children = node.children();
    while (children.hasMoreElements()) {
      CheckedTreeNode child = (CheckedTreeNode)children.nextElement();
      visitor.visit(child);
    }
  }

  private final class MyFilterComponent extends FilterComponent {
    private final TreeExpansionMonitor<DefaultMutableTreeNode> myExpansionMonitor = TreeExpansionMonitor.install(myTree);

    MyFilterComponent() {
      super("INTENTION_FILTER_HISTORY", 10);
    }

    @Override
    public void filter() {
      String filter = getFilter();
      if (filter != null && filter.length() > 0) {
        if (!myExpansionMonitor.isFreeze()) {
          myExpansionMonitor.freeze();
        }
      }
      IntentionSettingsTree.this.filter(filterModel(filter, true));
      if (myTree != null) {
        List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myTree);
        ((DefaultTreeModel)myTree.getModel()).reload();
        TreeUtil.restoreExpandedPaths(myTree, expandedPaths);
      }
      SwingUtilities.invokeLater(() -> {
        myTree.setSelectionRow(0);
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTree, true));
      });
      TreeUtil.expandAll(myTree);
      if (filter == null || filter.length() == 0) {
        TreeUtil.collapseAll(myTree, 0);
        myExpansionMonitor.restore();
      }
    }

    @Override
    protected void onlineFilter() {
      String filter = getFilter();
      if (filter != null && filter.length() > 0) {
        if (!myExpansionMonitor.isFreeze()) {
          myExpansionMonitor.freeze();
        }
      }
      IntentionSettingsTree.this.filter(filterModel(filter, true));
      TreeUtil.expandAll(myTree);
      if (filter == null || filter.length() == 0) {
        TreeUtil.collapseAll(myTree, 0);
        myExpansionMonitor.restore();
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
      if (!(value instanceof CheckedTreeNode node)) {
        return;
      }

      SimpleTextAttributes attributes = node.getUserObject() instanceof IntentionActionMetaData
                                        ? SimpleTextAttributes.REGULAR_ATTRIBUTES
                                        : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
      String text = getNodeText(node, false);
      Color background = UIUtil.getTreeBackground(selected, true);
      UIUtil.changeBackGround(this, background);
      SearchUtil.appendFragments(myFilter != null ? myFilter.getFilter() : null,
                                 text,
                                 attributes.getStyle(),
                                 attributes.getFgColor(),
                                 background,
                                 getTextRenderer());
    }

    @Override
    public @NotNull List<PropertyBean> getUiInspectorContext(@NotNull JTree tree, @Nullable Object value, int row) {
      if (value instanceof CheckedTreeNode node &&
          node.getUserObject() instanceof IntentionActionMetaData metaData) {
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
