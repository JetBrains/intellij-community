// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.*;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.ide.ui.customization.ActionUrl.*;
import static com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*;

public class CustomizableActionsPanel {
  private final JPanel myPanel = new BorderLayoutPanel(5, 5);
  protected JTree myActionsTree;
  private final JPanel myTopPanel = new BorderLayoutPanel();
  protected CustomActionsSchema mySelectedSchema;

  public CustomizableActionsPanel() {
    //noinspection HardCodedStringLiteral
    @SuppressWarnings("DialogTitleCapitalization")
    Group rootGroup = new Group("root", null, null);
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    MyActionsTreeModel model = new MyActionsTreeModel(root);
    myActionsTree = new Tree(model);
    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    myActionsTree.setCellRenderer(createDefaultRenderer());
    RowsDnDSupport.install(myActionsTree, model);

    patchActionsTreeCorrespondingToSchema(root);

    TreeExpansionMonitor.install(myActionsTree);
    myTopPanel.add(setupFilterComponent(myActionsTree), BorderLayout.WEST);
    myTopPanel.add(createToolbar(), BorderLayout.CENTER);

    myPanel.add(myTopPanel, BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myActionsTree), BorderLayout.CENTER);
  }

  private ActionToolbarImpl createToolbar() {
    ActionGroup addGroup = new DefaultActionGroup(new AddActionActionTreeSelectionAction()/*, new AddGroupAction()*/, new AddSeparatorAction());
    addGroup.getTemplatePresentation().setText(IdeBundle.message("group.customizations.add.action.group"));
    addGroup.getTemplatePresentation().setIcon(AllIcons.General.Add);
    addGroup.setPopup(true);
    ActionGroup restoreGroup = getRestoreGroup();
    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, new DefaultActionGroup(addGroup, new RemoveAction(), new EditIconAction(), new MoveUpAction(), new MoveDownAction(), restoreGroup), true);
    toolbar.setForceMinimumSize(true);
    toolbar.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY);
    toolbar.setTargetComponent(myTopPanel);
    return toolbar;
  }

  @NotNull
  protected ActionGroup getRestoreGroup() {
    ActionGroup restoreGroup = new DefaultActionGroup(new RestoreSelectionAction(), new RestoreAllAction());
    restoreGroup.setPopup(true);
    restoreGroup.getTemplatePresentation().setText(IdeBundle.message("group.customizations.restore.action.group"));
    restoreGroup.getTemplatePresentation().setIcon(AllIcons.Actions.Rollback);
    return restoreGroup;
  }

  static FilterComponent setupFilterComponent(JTree tree) {
    final TreeSpeedSearch mySpeedSearch = new TreeSpeedSearch(tree, true, null, new TreePathStringFunction()) {
      @Override
      public boolean isPopupActive() {
        return /*super.isPopupActive()*/true;
      }

      @Override
      public void showPopup(String searchText) {
        //super.showPopup(searchText);
      }

      @Override
      protected boolean isSpeedSearchEnabled() {
        return /*super.isSpeedSearchEnabled()*/false;
      }

      @Override
      public void showPopup() {
        //super.showPopup();
      }
    };
    mySpeedSearch.setupListeners();
    final FilterComponent filterComponent = new FilterComponent("CUSTOMIZE_ACTIONS", 5) {
      @Override
      public void filter() {
        mySpeedSearch.findAndSelectElement(getFilter());
        mySpeedSearch.getComponent().repaint();
      }
    };
    JTextField textField = filterComponent.getTextEditor();
    int[] keyCodes = {KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_UP, KeyEvent.VK_DOWN};
    for (int keyCode : keyCodes) {
      new DumbAwareAction(){
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          String filter = filterComponent.getFilter();
          if (!StringUtil.isEmpty(filter)) {
            mySpeedSearch.adjustSelection(keyCode, filter);
          }
        }
      }.registerCustomShortcutSet(keyCode, 0, textField);

    }
    return filterComponent;
  }

  private void addCustomizedAction(ActionUrl url) {
    mySelectedSchema.addAction(url);
  }

  private static boolean isMoveSupported(JTree tree, int dir) {
    final TreePath[] selectionPaths = tree.getSelectionPaths();
    if (selectionPaths != null) {
      DefaultMutableTreeNode parent = null;
      for (TreePath treePath : selectionPaths)
        if (treePath.getLastPathComponent() != null) {
          final DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
          if (parent == null) {
            parent = (DefaultMutableTreeNode)node.getParent();
          }
          if (parent == null || parent != node.getParent()) {
            return false;
          }
          if (dir > 0) {
            if (parent.getIndex(node) == parent.getChildCount() - 1) {
              return false;
            }
          }
          else {
            if (parent.getIndex(node) == 0) {
              return false;
            }
          }
        }
      return true;
    }
    return false;
  }


  public JPanel getPanel() {
    return myPanel;
  }

  public void apply() throws ConfigurationException {
    final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
    if (mySelectedSchema != null) {
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    restorePathsAfterTreeOptimization(treePaths);
    updateGlobalSchema();
    CustomActionsSchema.getInstance().initActionIcons();
    CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
    if (SystemInfo.isMac) {
      TouchbarSupport.reloadAllActions();
    }

    CustomActionsListener.fireSchemaChanged();
  }

  protected void updateGlobalSchema() {
    CustomActionsSchema.getInstance().copyFrom(mySelectedSchema);
  }

  protected void updateLocalSchema(CustomActionsSchema localSchema) {
  }

  private void restorePathsAfterTreeOptimization(final List<? extends TreePath> treePaths) {
    for (final TreePath treePath : treePaths) {
      myActionsTree.expandPath(CustomizationUtil.getPathByUserObjects(myActionsTree, treePath));
    }
  }

  public void reset() {
    reset(true);
  }

  public void resetToDefaults() {
    reset(false);
  }

  private void reset(boolean restoreLastState) {
    List<String> expandedIds = toActionIDs(TreeUtil.collectExpandedPaths(myActionsTree));
    List<String> selectedIds = toActionIDs(TreeUtil.collectSelectedPaths(myActionsTree));
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myActionsTree.getModel().getRoot();
    TreeUtil.treeNodeTraverser(root).traverse()
      .filter(node -> node instanceof DefaultMutableTreeNode && ((DefaultMutableTreeNode)node).getUserObject() instanceof Pair)
      .forEach(node -> doSetIcon(mySelectedSchema, (DefaultMutableTreeNode)node, null));
    CustomActionsSchema source = restoreLastState ? CustomActionsSchema.getInstance() : new CustomActionsSchema();
    if (mySelectedSchema == null) mySelectedSchema = new CustomActionsSchema();
    mySelectedSchema.copyFrom(source);
    updateLocalSchema(mySelectedSchema);
    mySelectedSchema.initActionIcons();
    patchActionsTreeCorrespondingToSchema(root);
    if (needExpandAll()) {
      new DefaultTreeExpander(myActionsTree).expandAll();
    } else {
      TreeUtil.restoreExpandedPaths(myActionsTree, toTreePaths(root, expandedIds));
    }
    TreeUtil.selectPaths(myActionsTree, toTreePaths(root, selectedIds));
    TreeUtil.ensureSelection(myActionsTree);
  }

  private static List<String> toActionIDs(List<? extends TreePath> paths) {
    return ContainerUtil.map(paths, path -> getActionId((DefaultMutableTreeNode)path.getLastPathComponent()));
  }

  private static List<TreePath> toTreePaths(DefaultMutableTreeNode root, List<String> actionIDs) {
    List<TreePath> result = new ArrayList<>();
    for (String actionId : actionIDs) {
      DefaultMutableTreeNode treeNode = TreeUtil.findNode(root, node -> Objects.equals(actionId, getActionId(node)));
      if (treeNode != null) result.add(TreeUtil.getPath(root, treeNode));
    }
    return result;
  }

  protected boolean needExpandAll() {
    return false;
  }

  public boolean isModified() {
    CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    return CustomActionsSchema.getInstance().isModified(mySelectedSchema);
  }

  protected void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
    root.removeAllChildren();
    if (mySelectedSchema != null) {
      mySelectedSchema.fillCorrectedActionGroups(root);
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
  }

  private static class TreePathStringFunction implements Function<TreePath, String> {
    @Override
    public String apply(TreePath o) {
      Object node = o.getLastPathComponent();
      if (node instanceof DefaultMutableTreeNode) {
        Object object = ((DefaultMutableTreeNode)node).getUserObject();
        if (object instanceof Group) return ((Group)object).getName();
        if (object instanceof QuickList) return ((QuickList)object).getName();
        String actionId;
        if (object instanceof String) {
          actionId = (String)object;
        }
        else if (object instanceof Pair) {
          Object obj = ((Pair<?, ?>)object).first;
          if (obj instanceof Group group) return group.getName();
          actionId = (String)obj;
        }
        else {
          return "";
        }
        if (Strings.isEmpty(actionId)) return "";
        AnAction action = ActionManager.getInstance().getAction(actionId);
        if (action != null) {
          return action.getTemplatePresentation().getText();
        }
      }
      return "";
    }
  }

  static TreeCellRenderer createDefaultRenderer() {
    return new MyTreeCellRenderer();
  }

  private static class MyTreeCellRenderer extends ColoredTreeCellRenderer {
    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      if (value instanceof DefaultMutableTreeNode) {
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        CustomizationUtil.acceptObjectIconAndText(userObject, (text, description, icon) -> {
          append(text);
          if (description != null) {
            append("   ", SimpleTextAttributes.REGULAR_ATTRIBUTES, false);
            append(description, SimpleTextAttributes.GRAY_ATTRIBUTES);
          }
          // do not show the icon for the top groups
          if (((DefaultMutableTreeNode)value).getLevel() > 1) {
            setIcon(icon);
          }
        });
        setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      }
    }
  }

  @Nullable
  static String getActionId(DefaultMutableTreeNode node) {
    Object obj = node.getUserObject();
    if (obj instanceof String actionId) return actionId;
    if (obj instanceof Group group) return group.getId();
    if (obj instanceof Pair<?, ?> pair) {
      Object first = pair.first;
      return first instanceof Group group ? group.getId() : (String)first;
    }
    return null;
  }

  @NotNull
  static Pair<@Nullable String, @Nullable Icon> getActionIdAndIcon(@NotNull DefaultMutableTreeNode node) {
    Object userObj = node.getUserObject();
    if (userObj instanceof String actionId) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        return Pair.create(actionId, action.getTemplatePresentation().getIcon());
      }
    }
    else if (userObj instanceof Group group) {
      return Pair.create(group.getId(), group.getIcon());
    }
    else if (userObj instanceof Pair<?, ?> pair) {
      Object first = pair.first;
      String actionId = first instanceof Group group ? group.getId() : (String)first;
      return Pair.create(actionId, (Icon)pair.second);
    }
    return Pair.empty();
  }

  static boolean setCustomIcon(@NotNull CustomActionsSchema schema,
                               @NotNull DefaultMutableTreeNode node,
                               @NotNull ActionIconInfo selectedInfo,
                               @Nullable Component component) {
    Pair<String, Icon> pair = getActionIdAndIcon(node);
    String actionId = pair.first;
    if (actionId != null) {
      Icon originalIcon = pair.second;
      Icon selectedIcon = selectedInfo.getIcon();
      if (selectedIcon != originalIcon) {
        String iconReference = selectedIcon != null ? selectedInfo.getIconReference() : null;
        return doSetIcon(schema, node, iconReference);
      }
    }
    return false;
  }

  private static boolean doSetIcon(@NotNull CustomActionsSchema schema,
                                   @NotNull DefaultMutableTreeNode node,
                                   @Nullable String path) {
    Object userObj = node.getUserObject();
    Object value = userObj instanceof Pair<?, ?> pair ? pair.first : userObj;
    String actionId = value instanceof Group group ? group.getId()
                                                   : value instanceof String ? (String)value : null;
    if (actionId == null) return false;
    if (StringUtil.isEmpty(path)) {
      node.setUserObject(Pair.create(value, null));
      schema.removeIconCustomization(actionId);
      return true;
    }
    ActionManager actionManager = ActionManager.getInstance();
    AnAction action = actionManager.getAction(actionId);
    if (action == null) return false;

    AnAction reuseFrom = actionManager.getAction(path);
    if (reuseFrom != null) {
      Icon toSet = CustomizationUtil.getOriginalIconFrom(reuseFrom);
      Icon defaultIcon = CustomizationUtil.getOriginalIconFrom(action);
      node.setUserObject(Pair.create(value, toSet));
      schema.addIconCustomization(actionId, toSet != defaultIcon ? path : null);
    }
    else {
      Icon icon;
      try {
        icon = CustomActionsSchema.loadCustomIcon(path);
      }
      catch (IOException e) {
        Logger.getInstance(CustomizableActionsPanel.class)
          .warn(String.format("Failed to load icon with path '%s' and set it to action '%s'", path, actionId));
        return false;
      }
      if (icon != null) {
        node.setUserObject(Pair.create(value, icon));
        schema.addIconCustomization(actionId, path);
      }
    }
    return true;
  }

  private class EditIconDialog extends DialogWrapper {
    private final DefaultMutableTreeNode myNode;
    private final boolean isNodeInsideMenu;
    private BrowseIconsComboBox myComboBox;

    protected EditIconDialog(TreePath path) {
      super(false);
      setTitle(IdeBundle.message("title.choose.action.icon"));
      isNodeInsideMenu = isInsideMenu(path);
      init();
      myNode = (DefaultMutableTreeNode)path.getLastPathComponent();
      myComboBox.selectIconForNode(myNode);
    }

    @Override
    public JComponent getPreferredFocusedComponent() {
      return myComboBox;
    }

    @Override
    protected String getDimensionServiceKey() {
      return getClass().getName();
    }

    @Override
    protected JComponent createCenterPanel() {
      myComboBox = new BrowseIconsComboBox(mySelectedSchema, getDisposable(), isNodeInsideMenu);
      JPanel northPanel = new JPanel(new BorderLayout());
      northPanel.add(myComboBox, BorderLayout.NORTH);
      return northPanel;
    }

    @Override
    protected void doOKAction() {
      Object selectedItem = myComboBox.getSelectedItem();
      if (selectedItem instanceof ActionIconInfo selectedInfo) {
        if (setCustomIcon(mySelectedSchema, myNode, selectedInfo, getContentPane())) {
          myActionsTree.repaint();
          CustomActionsSchema.setCustomizationSchemaForCurrentProjects();
        }
      }
      super.doOKAction();
    }
  }


  private abstract class TreeSelectionAction extends DumbAwareAction {
    private TreeSelectionAction(@NotNull Supplier<String> text) {
      super(text);
    }

    private TreeSelectionAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
      TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      if (selectionPaths == null) {
        e.getPresentation().setEnabled(false);
        return;
      }
      for (TreePath path : selectionPaths) {
        if (path.getPath().length <= minSelectionPathLength()) {
          e.getPresentation().setEnabled(false);
          return;
        }
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    protected int minSelectionPathLength() {
      return 2;
    }

    protected final boolean isSingleSelection() {
      final TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      return selectionPaths != null && selectionPaths.length == 1;
    }
  }

  private final class AddActionActionTreeSelectionAction extends TreeSelectionAction {
    private AddActionActionTreeSelectionAction() {
      super(IdeBundle.messagePointer("button.add.action"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      int row = myActionsTree.getRowForPath(selectionPath);
      if (selectionPath != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        AddActionDialog dlg = new AddActionDialog(mySelectedSchema, isInsideMenu(selectionPath));
        if (dlg.showAndGet()) {
          List<Object> addedActions = dlg.getAddedActions();
          if (!addedActions.isEmpty()) {
            boolean isGroupSelected = CustomizationUtil.getGroupForNode(node) != null;
            for (int ind = 0; ind < addedActions.size(); ind++) {
              Object action = addedActions.get(ind);
              if (action instanceof Group group) {
                group.setForceShowAsPopup(true);
              }
              int newActionPosition = isGroupSelected ? node.getChildCount() : node.getParent().getIndex(node) + ind + 1;
              ActionUrl url = new ActionUrl(getGroupPath(new TreePath(node.getPath()), true), action, ADDED, newActionPosition);
              addCustomizedAction(url);
              DefaultMutableTreeNode newNode = addPathToActionsTree(myActionsTree, url);
              if (newNode != null && action instanceof String) {
                Icon icon = CustomizationUtil.getIconForPath(ActionManager.getInstance(),mySelectedSchema.getIconPath((String)action));
                newNode.setUserObject(Pair.create(action, icon));
              }
            }

            ((DefaultTreeModel)myActionsTree.getModel()).reload();
            TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
            if (isGroupSelected) myActionsTree.expandPath(selectionPath);
            int newSelectedRow = row + (isGroupSelected ? node.getChildCount() : addedActions.size());
            myActionsTree.setSelectionRow(newSelectedRow);
          }
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        e.getPresentation().setEnabled(isSingleSelection());
      }
    }

    @Override
    protected int minSelectionPathLength() {
      return 1;
    }
  }

  private static boolean isInsideMenu(TreePath path) {
    if (path.getPathCount() < 2) return false;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getPathComponent(1);
    Group group = CustomizationUtil.getGroupForNode(node);
    if (group != null) {
      String groupId = group.getId();
      return groupId != null && groupId.contains("Menu");
    }
    return false;
  }

  private final class AddSeparatorAction extends TreeSelectionAction {
    private AddSeparatorAction() {
      super(IdeBundle.messagePointer("button.add.separator"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      if (selectionPath != null) {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)selectionPath.getLastPathComponent();
        final ActionUrl url = new ActionUrl(getGroupPath(selectionPath, false), Separator.getInstance(), ADDED,
                                            node.getParent().getIndex(node) + 1);
        changePathInActionsTree(myActionsTree, url);
        addCustomizedAction(url);
        ((DefaultTreeModel)myActionsTree.getModel()).reload();
      }
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      myActionsTree.setSelectionRow(myActionsTree.getRowForPath(selectionPath) + 1);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        e.getPresentation().setEnabled(isSingleSelection());
      }
    }
  }

  private class MyActionsTreeModel extends DefaultTreeModel implements EditableModel, RowsDnDSupport.RefinedDropSupport {
    private MyActionsTreeModel(TreeNode root) {
      super(root);
    }

    @Override
    public void addRow() {
    }

    @Override
    public void exchangeRows(int oldIndex, int newIndex) {
    }

    @Override
    public boolean canExchangeRows(int oldIndex, int newIndex) {
      return myActionsTree.getPathForRow(oldIndex).getPath().length > 2 && myActionsTree.getPathForRow(newIndex).getPath().length > 2;
    }

    @Override
    public void removeRow(int idx) {
    }

    @Override
    public boolean isDropInto(JComponent component, int oldIndex, int newIndex) {
      TreePath path = myActionsTree.getPathForRow(newIndex);
      return path.getPath().length > 1 && isGroupPath(path);
    }

    @Override
    public boolean canDrop(int oldIndex, int newIndex, @NotNull Position position) {
      TreePath target = myActionsTree.getPathForRow(newIndex);
      TreePath sourcePath = myActionsTree.getPathForRow(oldIndex);
      if (sourcePath.getParentPath().equals(target.getParentPath())) {
        if (oldIndex == newIndex - 1 && position == ABOVE) return false;
        if (oldIndex == newIndex + 1 && position == BELOW) return false;
      }

      if (sourcePath.getParentPath().equals(target) && position == INTO) return false;

      return sourcePath.getPath().length > 2 &&
             (target.getPath().length > 2 || target.getPath().length > 1 && isGroupPath(target));
    }

    @Override
    public void drop(int oldIndex, int newIndex, @NotNull Position position) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      TreePath path = myActionsTree.getPathForRow(oldIndex);
      TreePath targetPath = myActionsTree.getPathForRow(newIndex);
      if (Objects.equals(path.getParentPath(),
                         targetPath.getParentPath()) && position != INTO) {
        ActionUrl url = CustomizationUtil.getActionUrl(path, MOVE);
        url.setInitialPosition(url.getAbsolutePosition());
        int shift = position == ABOVE && oldIndex < newIndex ? -1: position == BELOW && oldIndex > newIndex ? 1 : 0;
        url.setAbsolutePosition(url.getInitialPosition() + newIndex - oldIndex + shift);
        changePathInActionsTree(myActionsTree, url);
        addCustomizedAction(url);
      } else {
        ActionUrl removeUrl = CustomizationUtil.getActionUrl(path, DELETED);
        changePathInActionsTree(myActionsTree, removeUrl);
        addCustomizedAction(removeUrl);
        ActionUrl addUrl = CustomizationUtil.getActionUrl(targetPath, ADDED);
        if (position == INTO) {
          addUrl.setAbsolutePosition(((DefaultMutableTreeNode)targetPath.getLastPathComponent()).getChildCount());
          if (TreeUtil.getUserObject(targetPath.getLastPathComponent()) instanceof Group group) {
            addUrl.getGroupPath().add(group.getName());
          }
        }
        addUrl.setComponent(removeUrl.getComponent());
        changePathInActionsTree(myActionsTree, addUrl);
        addCustomizedAction(addUrl);
      }

      ((DefaultTreeModel)myActionsTree.getModel()).reload();
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
      Object[] arr = Arrays.copyOf(targetPath.getParentPath().getPath(), targetPath.getPathCount());
      arr[arr.length - 1] = path.getLastPathComponent();
      TreePath pathToSelect = new TreePath(arr);
      TreeUtil.selectPath(myActionsTree, pathToSelect);
      TreeUtil.scrollToVisible(myActionsTree, path, false);
    }

    private static boolean isGroupPath(TreePath path) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      return CustomizationUtil.getGroupForNode(node) != null;
    }
  }


  private final class RemoveAction extends TreeSelectionAction {
    private RemoveAction() {
      super(IdeBundle.messagePointer("button.remove"), Presentation.NULL_STRING, AllIcons.General.Remove);
      ShortcutSet shortcutSet = KeymapUtil.filterKeyStrokes(CommonShortcuts.getDelete(),
                                                            KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0),
                                                            KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0));
      if (shortcutSet != null) {
        registerCustomShortcutSet(shortcutSet, myPanel);
      }
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      removePaths(myActionsTree.getSelectionPaths());
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
    }
  }

  private void removePaths(TreePath... paths) {
    if (paths == null) return;
    for (TreePath treePath : paths) {
      final ActionUrl url = CustomizationUtil.getActionUrl(treePath, DELETED);
      changePathInActionsTree(myActionsTree, url);
      addCustomizedAction(url);
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
  }

  private final class EditIconAction extends TreeSelectionAction {
    private EditIconAction() {
      super(IdeBundle.messagePointer("button.edit.action.icon"), Presentation.NULL_STRING, AllIcons.Actions.Edit);
      registerCustomShortcutSet(CommonShortcuts.getEditSource(), myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath selectionPath = myActionsTree.getLeadSelectionPath();
      if (selectionPath != null) {
        EditIconDialog dlg = new EditIconDialog(selectionPath);
        if (dlg.showAndGet()) {
          myActionsTree.repaint();
        }
      }
      TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        final ActionManager actionManager = ActionManager.getInstance();
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)myActionsTree.getLeadSelectionPath().getLastPathComponent();
        String actionId = getActionId(node);
        if (actionId != null) {
          final AnAction action = actionManager.getAction(actionId);
          e.getPresentation().setEnabled(action != null);
        }
        else {
          e.getPresentation().setEnabled(false);
        }

      }
    }
  }

  private final class MoveUpAction extends TreeSelectionAction {
    private MoveUpAction() {
      super(IdeBundle.messagePointer("button.move.up"), Presentation.NULL_STRING, AllIcons.Actions.MoveUp);
      registerCustomShortcutSet(CommonShortcuts.MOVE_UP, myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
      if (selectionPath != null) {
        for (TreePath treePath : selectionPath) {
          final ActionUrl url = CustomizationUtil.getActionUrl(treePath, MOVE);
          final int absolutePosition = url.getAbsolutePosition();
          url.setInitialPosition(absolutePosition);
          url.setAbsolutePosition(absolutePosition - 1);
          changePathInActionsTree(myActionsTree, url);
          addCustomizedAction(url);
        }
        ((DefaultTreeModel)myActionsTree.getModel()).reload();
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
        for (TreePath path : selectionPath) {
          myActionsTree.addSelectionPath(path);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(myActionsTree, -1));
    }
  }

  private final class MoveDownAction extends TreeSelectionAction {
    private MoveDownAction() {
      super(IdeBundle.messagePointer("button.move.down"), Presentation.NULL_STRING, AllIcons.Actions.MoveDown);
      registerCustomShortcutSet(CommonShortcuts.MOVE_DOWN, myPanel);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath[] selectionPath = myActionsTree.getSelectionPaths();
      if (selectionPath != null) {
        for (int i = selectionPath.length - 1; i >= 0; i--) {
          TreePath treePath = selectionPath[i];
          final ActionUrl url = CustomizationUtil.getActionUrl(treePath, MOVE);
          final int absolutePosition = url.getAbsolutePosition();
          url.setInitialPosition(absolutePosition);
          url.setAbsolutePosition(absolutePosition + 1);
          changePathInActionsTree(myActionsTree, url);
          addCustomizedAction(url);
        }
        ((DefaultTreeModel)myActionsTree.getModel()).reload();
        TreeUtil.restoreExpandedPaths(myActionsTree, expandedPaths);
        for (TreePath path : selectionPath) {
          myActionsTree.addSelectionPath(path);
        }
      }
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(myActionsTree, 1));
    }
  }

  private final class RestoreSelectionAction extends DumbAwareAction {
    private RestoreSelectionAction() {
      super(IdeBundle.messagePointer("button.restore.selected.groups"));
    }

    private Pair<TreeSet<String>, List<ActionUrl>> findActionsUnderSelection() {
      ArrayList<ActionUrl> actions = new ArrayList<>();
      TreeSet<String> selectedNames = new TreeSet<>();
      TreePath[] selectionPaths = myActionsTree.getSelectionPaths();
      if (selectionPaths != null) {
        for (TreePath path : selectionPaths) {
          ActionUrl selectedUrl = CustomizationUtil.getActionUrl(path, MOVE);
          ArrayList<String> selectedGroupPath = new ArrayList<>(selectedUrl.getGroupPath());
          Object component = selectedUrl.getComponent();
          if (component instanceof Group) {
            selectedGroupPath.add(((Group)component).getName());
            selectedNames.add(((Group)component).getName());
            for (ActionUrl action : mySelectedSchema.getActions()) {
              ArrayList<String> groupPath = action.getGroupPath();
              int idx = Collections.indexOfSubList(groupPath, selectedGroupPath);
              if (idx > -1) {
                actions.add(action);
              }
            }
          }
        }
      }
      return Pair.create(selectedNames, actions);
    }


    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<ActionUrl> otherActions = new ArrayList<>(mySelectedSchema.getActions());
      otherActions.removeAll(findActionsUnderSelection().second);
      mySelectedSchema.copyFrom(new CustomActionsSchema());
      for (ActionUrl otherAction : otherActions) {
        mySelectedSchema.addAction(otherAction);
      }
      final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
      restorePathsAfterTreeOptimization(treePaths);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Pair<TreeSet<String>, List<ActionUrl>> selection = findActionsUnderSelection();
      e.getPresentation().setEnabled(!selection.second.isEmpty());
      if (selection.first.size() != 1) {
        e.getPresentation().setText(IdeBundle.messagePointer("button.restore.selected.groups"));
      }
      else {
        e.getPresentation().setText(IdeBundle.messagePointer("button.restore.selection", selection.first.iterator().next()));
      }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }
  }

  private final class RestoreAllAction extends DumbAwareAction {
    private RestoreAllAction() {
      super(IdeBundle.messagePointer("button.restore.all"));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      mySelectedSchema.copyFrom(new CustomActionsSchema());
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySelectedSchema.isModified(new CustomActionsSchema()));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
