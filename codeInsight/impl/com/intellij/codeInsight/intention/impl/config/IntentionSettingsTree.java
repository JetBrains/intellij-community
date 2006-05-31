/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.ide.CommonActionsManager;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.util.Ref;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.FilterComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

public abstract class IntentionSettingsTree {
  private JComponent myComponent;
  private CheckboxTree myTree;
  private FilterComponent myFilter;
  protected IntentionSettingsTree() {
    initTree();
  }

  public JTree getTree(){
    return myTree;
  }

  public JComponent getComponent() {
    return myComponent;
  }

  private void initTree() {
    myTree = new CheckboxTree(new CheckboxTree.CheckboxTreeCellRenderer(true) {
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        CheckedTreeNode node = (CheckedTreeNode)value;
        SimpleTextAttributes attributes = node.getUserObject() instanceof IntentionActionMetaData ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES;
        final String text = getNodeText(node);
        if (text != null) {
          SearchUtil.appendFragments(myFilter != null ? myFilter.getFilter() : null,
                                     text,
                                     attributes.getStyle(),
                                     attributes.getFgColor(),
                                     selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground(),
                                     getTextRenderer());
        }
      }
    }, new CheckedTreeNode(null)){
      protected void checkNode(CheckedTreeNode node, boolean checked) {
        super.checkNode(node,checked);
        checkRecursively(node, checked);
        updateCheckMarkInParents(node);
      }
    };

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      public void valueChanged(TreeSelectionEvent e) {
        TreePath path = e.getPath();
        Object userObject = ((CheckedTreeNode)path.getLastPathComponent()).getUserObject();
        selectionChanged(userObject);
      }
    });

    myFilter = new MyFilterComponent();
    myComponent = new JPanel(new BorderLayout());
    JScrollPane scrollPane = new JScrollPane(myTree);
    JPanel toolbarPanel = new JPanel(new BorderLayout());
    toolbarPanel.add(createTreeToolbarPanel().getComponent(), BorderLayout.WEST);
    toolbarPanel.add(myFilter, BorderLayout.EAST);
    myComponent.add(toolbarPanel, BorderLayout.NORTH);
    myComponent.add(scrollPane, BorderLayout.CENTER);

    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        myTree.setSelectionRow(0);
      }
    });
    myFilter.reset();
  }

  private ActionToolbar createTreeToolbarPanel() {
    final CommonActionsManager actionManager = CommonActionsManager.getInstance();

    TreeExpander treeExpander = new TreeExpander() {
      public void expandAll() {
        TreeUtil.expandAll(myTree);
      }

      public boolean canExpand() {
        return true;
      }

      public void collapseAll() {
        TreeUtil.collapseAll(myTree, 3);
      }

      public boolean canCollapse() {
        return true;
      }
    };

    AnAction expandAllToolbarAction = actionManager.createExpandAllAction(treeExpander);
    expandAllToolbarAction.registerCustomShortcutSet(expandAllToolbarAction.getShortcutSet(), myTree);
    DefaultActionGroup actions = new DefaultActionGroup();
    actions.add(expandAllToolbarAction);

    AnAction collapseAllToolbarAction = actionManager.createCollapseAllAction(treeExpander);
    collapseAllToolbarAction.registerCustomShortcutSet(collapseAllToolbarAction.getShortcutSet(), myTree);
    actions.add(collapseAllToolbarAction);

    return ActionManager.getInstance().createActionToolbar(ActionPlaces.MAIN_TOOLBAR, actions, true);
  }

  private static void updateCheckMarkInParents(CheckedTreeNode node) {
    while (node.getParent() != null) {
      final CheckedTreeNode parent = (CheckedTreeNode)node.getParent();
      parent.setChecked(false);
      visitChildren(parent, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          if (node.isChecked()) {
            parent.setChecked(true);
          }
        }
      });
      node = parent;
    }
  }

  protected abstract void selectionChanged(Object selected);
  protected abstract List<IntentionActionMetaData> filterModel(String filter, final boolean force);

  public void reset(List<IntentionActionMetaData> intentionsToShow) {
    CheckedTreeNode root = new CheckedTreeNode(null);
    final DefaultTreeModel treeModel = (DefaultTreeModel)myTree.getModel();
    intentionsToShow = sort(intentionsToShow);

    for (final IntentionActionMetaData metaData : intentionsToShow) {
      String[] category = metaData.myCategory;
      CheckedTreeNode node = root;
      for (final String name : category) {
        CheckedTreeNode child = findChild(node, name);
        if (child == null) {
          CheckedTreeNode newChild = new CheckedTreeNode(name);
          treeModel.insertNodeInto(newChild, node, node.getChildCount());
          child = newChild;
        }
        node = child;
      }
      CheckedTreeNode newChild = new CheckedTreeNode(metaData);
      treeModel.insertNodeInto(newChild, node, node.getChildCount());
    }
    resetCheckMark(root);
    treeModel.setRoot(root);
    treeModel.nodeChanged(root);
  }

  private static List<IntentionActionMetaData> sort(final List<IntentionActionMetaData> intentionsToShow) {
    List<IntentionActionMetaData> copy = new ArrayList<IntentionActionMetaData>(intentionsToShow);
    Collections.sort(copy, new Comparator<IntentionActionMetaData>() {
      public int compare(final IntentionActionMetaData data1, final IntentionActionMetaData data2) {
        String[] category1 = data1.myCategory;
        String[] category2 = data2.myCategory;
        int result = ArrayUtil.lexicographicCompare(category1, category2);
        if (result!= 0) {
          return result;
        }
        return data1.myFamily.compareTo(data2.myFamily);
      }
    });
    return copy;
  }

  private CheckedTreeNode getRoot() {
    return (CheckedTreeNode)myTree.getModel().getRoot();
  }

  private static boolean resetCheckMark(final CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData) {
      IntentionActionMetaData metaData = (IntentionActionMetaData)userObject;
      boolean enabled = IntentionManagerSettings.getInstance().isEnabled(metaData.myFamily);
      root.setChecked(enabled);
      return enabled;
    }
    else {
      root.setChecked(false);
      visitChildren(root, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          if (resetCheckMark(node)) {
            root.setChecked(true);
          }
        }
      });
      return root.isChecked();
    }
  }

  private static void checkRecursively(CheckedTreeNode root, final boolean check) {
    Object userObject = root.getUserObject();
    root.setChecked(check);
    if (!(userObject instanceof IntentionActionMetaData)) {
      visitChildren(root, new CheckedNodeVisitor() {
        public void visit(CheckedTreeNode node) {
          checkRecursively(node, check);
        }
      });
    }
  }

  private static CheckedTreeNode findChild(CheckedTreeNode node, final String name) {
    final Ref<CheckedTreeNode> found = new Ref<CheckedTreeNode>();
    visitChildren(node, new CheckedNodeVisitor() {
      public void visit(CheckedTreeNode node) {
        String text = getNodeText(node);
        if (name.equals(text)) {
          found.set(node);
        }
      }
    });
    return found.get();
  }

  private static String getNodeText(CheckedTreeNode node) {
    final Object userObject = node.getUserObject();
    String text;
    if (userObject instanceof String) {
      text = (String)userObject;
    }
    else if (userObject instanceof IntentionActionMetaData) {
      text = ((IntentionActionMetaData)userObject).myFamily;
    }
    else {
      text = "???";
    }
    return text;
  }

  public void apply() {
    CheckedTreeNode root = getRoot();
    apply(root);
  }

  private static void apply(CheckedTreeNode root) {
    Object userObject = root.getUserObject();
    if (userObject instanceof IntentionActionMetaData) {
      IntentionActionMetaData actionMetaData = (IntentionActionMetaData)userObject;
      IntentionManagerSettings.getInstance().setEnabled(actionMetaData.myFamily, root.isChecked());
    }
    else {
      visitChildren(root, new CheckedNodeVisitor() {
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
    if (userObject instanceof IntentionActionMetaData) {
      IntentionActionMetaData actionMetaData = (IntentionActionMetaData)userObject;
      boolean enabled = IntentionManagerSettings.getInstance().isEnabled(actionMetaData.myFamily);
      return enabled != root.isChecked();
    }
    else {
      final boolean[] modified = new boolean[] { false };
      visitChildren(root, new CheckedNodeVisitor() {
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

  public void setFilter(String filter){
    myFilter.setFilter(filter);
  }

  public String getFilter() {
    return myFilter.getFilter();
  }

  interface CheckedNodeVisitor {
    void visit(CheckedTreeNode node);
  }
  private static void visitChildren(CheckedTreeNode node, CheckedNodeVisitor visitor) {
    Enumeration children = node.children();
    while (children.hasMoreElements()) {
      final CheckedTreeNode child = (CheckedTreeNode)children.nextElement();
      visitor.visit(child);
    }
  }

  private class MyFilterComponent extends FilterComponent {
    private TreeExpansionMonitor<DefaultMutableTreeNode> myExpansionMonitor = TreeExpansionMonitor.install(myTree);

    public MyFilterComponent() {
      super("INTENTION_FILTER_HISTORY", 10);
    }

    public void filter() {
      final String filter = getFilter();
      if (filter != null && filter.length() > 0) {
        if (!myExpansionMonitor.isFreeze()) {
          myExpansionMonitor.freeze();
        }
      }
      IntentionSettingsTree.this.reset(filterModel(filter, true));
      if (myTree != null) {
        List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myTree);
        ((DefaultTreeModel)myTree.getModel()).reload();
        TreeUtil.restoreExpandedPaths(myTree, expandedPaths);
      }
      SwingUtilities.invokeLater(new Runnable() {
        public void run() {
          myTree.setSelectionRow(0);
          myTree.requestFocus();
        }
      });
      TreeUtil.expandAll(myTree);
      if (filter == null || filter.length() == 0) {
        TreeUtil.collapseAll(myTree, 0);
        myExpansionMonitor.restore();
      }
    }

    protected void onlineFilter() {
      final String filter = getFilter();
      if (filter != null && filter.length() > 0) {
        if (!myExpansionMonitor.isFreeze()) {
          myExpansionMonitor.freeze();
        }
      }
      IntentionSettingsTree.this.reset(filterModel(filter, true));
      TreeUtil.expandAll(myTree);
      if (filter == null || filter.length() == 0) {
        TreeUtil.collapseAll(myTree, 0);
        myExpansionMonitor.restore();
      }
    }
  }
}