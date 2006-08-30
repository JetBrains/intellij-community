package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.DataManager;
import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.util.Alarm;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.TreeTableModel;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;

public class ActionsTree {
  private static final Icon EMPTY_ICON = new EmptyIcon(18, 18);
  private static final Icon QUICK_LIST_ICON = IconLoader.getIcon("/actions/quickList.png");
  private static final Icon OPEN_ICON = new DefaultTreeCellRenderer().getOpenIcon();
  private static final Icon CLOSE_ICON = new DefaultTreeCellRenderer().getClosedIcon();

  private JTree myTree;
  private DefaultMutableTreeNode myRoot;
  private JScrollPane myComponent;
  private Keymap myKeymap;
  private Group myMainGroup = new Group("", null, null);

  @NonNls
  private static final String ROOT = "ROOT";

  private String myFilter = null;

  public ActionsTree() {
    myRoot = new DefaultMutableTreeNode(ROOT);

    myTree = new JTree(new MyModel(myRoot));
    myTree.setCellRenderer(new ColoredTreeCellRenderer(){
      public void customizeCellRenderer(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        Keymap originalKeymap = myKeymap != null ? myKeymap.getParent() : null;
        Icon icon = null;
        String text;
        if (value instanceof DefaultMutableTreeNode) {
          Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          boolean changed;
          if (userObject instanceof Group) {
            Group group = (Group)userObject;
            text = group.getName();

            changed = originalKeymap != null && isGroupChanged(group, originalKeymap, myKeymap);
            icon = expanded ? group.getOpenIcon() : group.getIcon();
            if (icon == null){
              icon = expanded ? OPEN_ICON : CLOSE_ICON;
            }
          }
          else if (userObject instanceof String) {
            String actionId = (String)userObject;
            AnAction action = ActionManager.getInstance().getActionOrStub(actionId);
            text = action != null ? action.getTemplatePresentation().getText() : actionId;
            if (action != null) {
              Icon actionIcon = action.getTemplatePresentation().getIcon();
              if (actionIcon != null) {
                icon = actionIcon;
              }
            }
            changed = originalKeymap != null && isActionChanged(actionId, originalKeymap, myKeymap);
          }
          else if (userObject instanceof QuickList) {
            QuickList list = (QuickList)userObject;
            icon = QUICK_LIST_ICON;
            text = list.getDisplayName();

            changed = originalKeymap != null && isActionChanged(list.getActionId(), originalKeymap, myKeymap);
          }
          else if (userObject instanceof Separator) {
            // TODO[vova,anton]: beautify
            changed = false;
            text = "-------------";
          }
          else {
            throw new IllegalArgumentException("unknown userObject: " + userObject);
          }

          LayeredIcon layeredIcon = new LayeredIcon(2);
          layeredIcon.setIcon(EMPTY_ICON, 0);
          if (icon != null){
            layeredIcon.setIcon(icon, 1, (- icon.getIconWidth() + EMPTY_ICON.getIconWidth())/2, (EMPTY_ICON.getIconHeight() - icon.getIconHeight())/2);
          }
          setIcon(layeredIcon);

          Color foreground;
          if (selected && hasFocus) {
            foreground = UIUtil.getTreeSelectionForeground();
          }
          else {
            if (changed) {
              foreground = Color.BLUE;
            }
            else {
              foreground = UIUtil.getTreeForeground();
            }
          }
          SearchUtil.appendFragments(myFilter, text, Font.PLAIN, foreground, selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground(), this);
        }
      }
    });

    myTree.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    myComponent = ScrollPaneFactory.createScrollPane(myTree);
  }

  public JComponent getComponent() {
    return myComponent;
  }

  public void addTreeSelectionListener(TreeSelectionListener l) {
    myTree.getSelectionModel().addTreeSelectionListener(l);
  }

  private Object getSelectedObject() {
    TreePath selectionPath = myTree.getSelectionPath();
    if (selectionPath == null) return null;
    Object userObject = ((DefaultMutableTreeNode)selectionPath.getLastPathComponent()).getUserObject();
    return userObject;
  }

  public String getSelectedActionId() {
    Object userObject = getSelectedObject();
    if (userObject instanceof String) return (String)userObject;
    if (userObject instanceof QuickList) return ((QuickList)userObject).getActionId();
    return null;
  }

  public QuickList getSelectedQuickList() {
    Object userObject = getSelectedObject();
    if (!(userObject instanceof QuickList)) return null;
    return (QuickList)userObject;
  }

  public void reset(Keymap keymap, final QuickList[] allQuickLists) {
    reset(keymap, allQuickLists, null, null);
  }

  public Group getMainGroup() {
    return myMainGroup;
  }

  public JTree getTree(){
    return myTree;
  }

  public void filter(final String filter, final QuickList[] currentQuickListIds) {
    myFilter = filter;
    reset(myKeymap, currentQuickListIds, filter, null);
  }

  private void reset(final Keymap keymap, final QuickList[] allQuickLists, String filter, KeyboardShortcut shortcut) {
    myKeymap = keymap;

    final PathsKeeper pathsKeeper = new PathsKeeper();
    pathsKeeper.storePaths();

    myRoot.removeAllChildren();

    ActionManager actionManager = ActionManager.getInstance();
    Project project = (Project)DataManager.getInstance().getDataContext(getComponent()).getData(DataConstants.PROJECT);
    Group mainGroup = ActionsTreeUtil.createMainGroup(project, myKeymap, allQuickLists, filter, true, filter != null ?
                                                                                                      ActionsTreeUtil.isActionFiltered(filter, true) :
                                                                                                      (shortcut != null ? ActionsTreeUtil.isActionFiltered(actionManager, myKeymap, shortcut, true) : null));
    if ((filter != null || shortcut != null) && mainGroup.initIds().isEmpty()){
      mainGroup = ActionsTreeUtil.createMainGroup(project, myKeymap, allQuickLists, filter, false, filter != null ?
                                                                                                   ActionsTreeUtil.isActionFiltered(filter, false) :
                                                                                                   ActionsTreeUtil.isActionFiltered(actionManager, myKeymap, shortcut, false));
    }
    myRoot = ActionsTreeUtil.createNode(mainGroup);
    myMainGroup = mainGroup;
    MyModel model = (MyModel)myTree.getModel();
    model.setRoot(myRoot);
    model.nodeStructureChanged(myRoot);

    pathsKeeper.restorePaths();
  }

  public void filterTree(final KeyboardShortcut keyboardShortcut, final QuickList [] currentQuickListIds) {
    reset(myKeymap, currentQuickListIds, null, keyboardShortcut);
  }

  private class MyModel extends DefaultTreeModel implements TreeTableModel {
    protected MyModel(DefaultMutableTreeNode root) {
      super(root);
    }

    public int getColumnCount() {
      return 2;
    }

    public String getColumnName(int column) {
      switch (column) {
        case 0: return KeyMapBundle.message("action.column.name");
        case 1: return KeyMapBundle.message("shortcuts.column.name");
      }
      return "";
    }

    public Object getValueAt(Object value, int column) {
      if (!(value instanceof DefaultMutableTreeNode)) {
        return "???";
      }

      if (column == 0) {
        return value;
      }
      else if (column == 1) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        if (userObject instanceof QuickList) {
          userObject = ((QuickList)userObject).getActionId();
        }

        if (userObject instanceof String) {
          Shortcut[] shortcuts = myKeymap.getShortcuts((String)userObject);
          return KeymapUtil.getShortcutsText(shortcuts);
        }
        else {
          return "";
        }
      }
      else {
        return "???";
      }
    }

    public Object getChild(Object parent, int index) {
      return ((TreeNode)parent).getChildAt(index);
    }

    public int getChildCount(Object parent) {
      return ((TreeNode)parent).getChildCount();
    }

    public Class getColumnClass(int column) {
      if (column == 0) {
        return TreeTableModel.class;
      }
      else {
        return Object.class;
      }
    }

    public boolean isCellEditable(Object node, int column) {
      return column == 0;
    }

    public void setValueAt(Object aValue, Object node, int column) {
    }
  }


  private static boolean isActionChanged(String actionId, Keymap oldKeymap, Keymap newKeymap) {
    Shortcut[] oldShortcuts = oldKeymap.getShortcuts(actionId);
    Shortcut[] newShortcuts = newKeymap.getShortcuts(actionId);
    return !Comparing.equal(oldShortcuts, newShortcuts);
  }

  private static boolean isGroupChanged(Group group, Keymap oldKeymap, Keymap newKeymap) {
    ArrayList children = group.getChildren();
    for (Object child : children) {
      if (child instanceof Group) {
        if (isGroupChanged((Group)child, oldKeymap, newKeymap)) {
          return true;
        }
      }
      else if (child instanceof String) {
        String actionId = (String)child;
        if (isActionChanged(actionId, oldKeymap, newKeymap)) {
          return true;
        }
      }
      else if (child instanceof QuickList) {
        String actionId = ((QuickList)child).getActionId();
        if (isActionChanged(actionId, oldKeymap, newKeymap)) {
          return true;
        }
      }
    }
    return false;
  }

  public void selectAction(String actionId) {
    final JTree tree = myTree;

    String path = myMainGroup.getActionQualifiedPath(actionId);
    if (path == null) {
      return;
    }
    final DefaultMutableTreeNode node = getNodeForPath(path);
    if (node == null) {
      return;
    }
    tree.expandPath(new TreePath(((DefaultMutableTreeNode)node.getParent()).getPath()));

    Alarm alarm = new Alarm();
    alarm.addRequest(new Runnable() {
      public void run() {
        JTree tree = myTree;
        Rectangle pathBounds = tree.getPathBounds(new TreePath(node.getPath()));
        myTree.scrollRectToVisible(pathBounds);
      }
    }, 100);
  }

  private DefaultMutableTreeNode getNodeForPath(String path) {
    Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      if (Comparing.equal(getPath(node), path)) {
        return node;
      }
    }
    return null;
  }

  private ArrayList<DefaultMutableTreeNode> getNodesByPaths(ArrayList<String> paths){
    final ArrayList<DefaultMutableTreeNode> result = new ArrayList<DefaultMutableTreeNode>();
    Enumeration enumeration = ((DefaultMutableTreeNode)myTree.getModel().getRoot()).preorderEnumeration();
    while (enumeration.hasMoreElements()) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)enumeration.nextElement();
      final String path = getPath(node);
      if (paths.contains(path)) {
        result.add(node);
      }
    }
    return result;
  }

  private String getPath(DefaultMutableTreeNode node) {
    Object userObject = node.getUserObject();
    if (userObject instanceof String) {
      String actionId = (String)userObject;
      return myMainGroup.getActionQualifiedPath(actionId);
    }
    if (userObject instanceof Group) {
      return ((Group)userObject).getQualifiedPath();
    }
    return null;
  }

  private class PathsKeeper {
    private ArrayList<String> myPathsToExpand;
    private ArrayList<String> mySelectionPaths;

    public void storePaths() {
      myPathsToExpand = new ArrayList<String>();
      mySelectionPaths = new ArrayList<String>();

      DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();

      TreePath path = new TreePath(root.getPath());
      if (myTree.isPathSelected(path)){
        mySelectionPaths.add(getPath(root));
      }
      if (myTree.isExpanded(path) || root.getChildCount() == 0){
        myPathsToExpand.add(getPath(root));
        _storePaths(root);
      }
    }

    private void _storePaths(DefaultMutableTreeNode root) {
      ArrayList<TreeNode> childNodes = childrenToArray(root);
      for (final Object childNode1 : childNodes) {
        DefaultMutableTreeNode childNode = (DefaultMutableTreeNode)childNode1;
        TreePath path = new TreePath(childNode.getPath());
        if (myTree.isPathSelected(path)) {
          mySelectionPaths.add(getPath(childNode));
        }
        if (myTree.isExpanded(path) || childNode.getChildCount() == 0) {
          myPathsToExpand.add(getPath(childNode));
          _storePaths(childNode);
        }
      }
    }

    public void restorePaths() {
      final ArrayList<DefaultMutableTreeNode> nodesToExpand = getNodesByPaths(myPathsToExpand);
      for (DefaultMutableTreeNode node : nodesToExpand) {
        myTree.expandPath(new TreePath(node.getPath()));
      }

      Alarm alarm = new Alarm();
      alarm.addRequest(new Runnable() {
        public void run() {
          final ArrayList<DefaultMutableTreeNode> nodesToSelect = getNodesByPaths(mySelectionPaths);
          for (DefaultMutableTreeNode node : nodesToSelect) {
            TreeUtil.selectNode(myTree, node);
          }
        }
      }, 100);
    }


    private ArrayList<TreeNode> childrenToArray(DefaultMutableTreeNode node) {
      ArrayList<TreeNode> arrayList = new ArrayList<TreeNode>();
      for(int i = 0; i < node.getChildCount(); i++){
        arrayList.add(node.getChildAt(i));
      }
      return arrayList;
    }
  }
}
