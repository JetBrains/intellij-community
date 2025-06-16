// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.packageDependencies.ui.TreeExpansionMonitor;
import com.intellij.ui.*;
import com.intellij.ui.dsl.gridLayout.GridLayout;
import com.intellij.ui.mac.touchbar.TouchbarSupport;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EditableModel;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.ide.ui.customization.ActionUrl.*;
import static com.intellij.ui.RowsDnDSupport.RefinedDropSupport.Position.*;

@ApiStatus.Internal
public class CustomizableActionsPanel {
  private static final DataKey<TreePath[]> SELECTION = DataKey.create("CUSTOMIZABLE_ACTIONS_PANEL_SELECTION");
  private static final DataKey<TreePath> LEAD_SELECTION = DataKey.create("CUSTOMIZABLE_ACTIONS_PANEL_LEAD_SELECTION");
  private final JPanel myPanel = new MyPanel();
  protected JTree myActionsTree;
  protected CustomActionsSchema mySelectedSchema;
  private final Computable<Integer> myPreferredHeightProvider;

  private class MyPanel extends BorderLayoutPanel implements UiDataProvider {
    private MyPanel() {
      super(5, 5);
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(SELECTION, myActionsTree.getSelectionPaths());
      sink.set(LEAD_SELECTION, myActionsTree.getLeadSelectionPath());
    }
  }

  public CustomizableActionsPanel() {
    //noinspection HardCodedStringLiteral
    @SuppressWarnings("DialogTitleCapitalization")
    Group rootGroup = new Group("root");
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    MyActionsTreeModel model = new MyActionsTreeModel(root);
    myActionsTree = new Tree(model);
    myActionsTree.setRootVisible(false);
    myActionsTree.setShowsRootHandles(true);
    myActionsTree.setCellRenderer(createDefaultRenderer());
    RowsDnDSupport.install(myActionsTree, model);
    PopupHandler.installPopupMenu(myActionsTree, createPopupActionGroup(), ActionPlaces.CUSTOMIZE_ACTIONS_PANEL);

    TreeExpansionMonitor.install(myActionsTree);
    JComponent filter = setupFilterComponent(myActionsTree);
    myPreferredHeightProvider = new Computable<>() {
      @Override
      public Integer compute() {
        return filter.getPreferredSize().height;
      }
    };

    JPanel topPanel = new JPanel(new GridLayout());
    CustomizationActionPanelLayoutUtilsKt.setupTopPanelLayout(topPanel, createToolbar(), filter);

    myPanel.add(topPanel, BorderLayout.NORTH);
    myPanel.add(ScrollPaneFactory.createScrollPane(myActionsTree), BorderLayout.CENTER);
  }

  private ActionGroup createPopupActionGroup() {
    return new DefaultActionGroup(new EditIconAction(), new RemoveAction(), new Separator(), new AddActionBelowSelectionAction(), new AddSeparatorAction());
  }

  private JComponent createToolbar() {
    JPanel container = new JPanel(new BorderLayout());

    ActionToolbarImpl addGroupToolbar = (ActionToolbarImpl)ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR, new DefaultActionGroup(new AddActionActionTreeSelectionAction()), true);
    addGroupToolbar.setTargetComponent(myPanel);
    addGroupToolbar.setActionButtonBorder(new JBEmptyBorder(0));
    addGroupToolbar.setBorder(new JBEmptyBorder(0));
    container.add(addGroupToolbar, BorderLayout.WEST);

    ActionToolbarImpl toolbar = (ActionToolbarImpl)ActionManager.getInstance()
      .createActionToolbar(ActionPlaces.TOOLBAR,
                           new DefaultActionGroup(new EditIconAction(), new MoveUpAction(), new MoveDownAction(), new Separator(),
                                                  new RemoveAction(), getRestoreGroup()), true);
    toolbar.setForceMinimumSize(true);
    toolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY);
    toolbar.setTargetComponent(myPanel);
    container.add(toolbar, BorderLayout.CENTER);

    return container;
  }

  protected @NotNull ActionGroup getRestoreGroup() {
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
    filterComponent.setMaximumSize(new Dimension(300, 300));
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
    onModified();
  }

  private static boolean isMoveSupported(@NotNull AnActionEvent e, int dir) {
    final TreePath[] selectionPaths = e.getData(SELECTION);
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

  @RequiresBackgroundThread // loading extensions might be required here
  private static CustomizationRoots getCustomizationRoots() {
    return new CustomizationRoots(ActionGroupCustomizationService.getInstance().getReadOnlyActionGroupIds());
  }

  private static class CustomizationRoots {
    private final @NotNull Set<String> myReadOnlyActionGroupIds;

    CustomizationRoots(@NotNull Set<String> readOnlyActionGroupIds) {
      myReadOnlyActionGroupIds = readOnlyActionGroupIds;
    }

    private boolean isUnderCustomizationRoot(@Nullable TreePath path) {
      if (path == null) return false;
      var parent = path.getParentPath();
      return isCustomizationRoot(parent) || isUnderCustomizationRoot(parent);
    }

    private boolean isCustomizationRoot(@Nullable TreePath path) {
      if (path == null) return false;
      if (isReadOnlyGroup(path)) return false; // can't customize read-only groups, only their children
      var parent = path.getParentPath();
      if (parent == null) return false; // we have an invisible root in the tree, so all top-level groups have a parent
      return isReadOnlyGroup(parent);
    }

    private boolean isReadOnlyGroup(@Nullable TreePath path) {
      if (path == null) return false;
      var group = CustomizationUtil.getGroupForNode((DefaultMutableTreeNode)path.getLastPathComponent());
      if (group == null) return false;
      if (myReadOnlyActionGroupIds.contains(group.getId())) return true;
      return path.getPathCount() == 1; // by default, only the invisible root is read-only
    }
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
    CustomActionsSchema customActionsSchema = CustomActionsSchema.getInstance();
    customActionsSchema.initActionIcons();
    customActionsSchema.setCustomizationSchemaForCurrentProjects();
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
    CustomActionsSchema source = restoreLastState ? CustomActionsSchema.getInstance() : new CustomActionsSchema(null);
    if (mySelectedSchema == null) mySelectedSchema = new CustomActionsSchema(null);
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
    onModified();
  }

  protected void onModified() { }

  private static @Unmodifiable List<String> toActionIDs(List<? extends TreePath> paths) {
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
    return isModified(true);
  }

  boolean isModified(boolean optimized) {
    if (optimized) {
      CustomizationUtil.optimizeSchema(myActionsTree, mySelectedSchema);
    }
    return CustomActionsSchema.getInstance().isModified(mySelectedSchema);
  }

  protected void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
    root.removeAllChildren();
    if (mySelectedSchema != null) {
      mySelectedSchema.fillCorrectedActionGroups(root);
    }
    ((DefaultTreeModel)myActionsTree.getModel()).reload();
    onModified();
  }

  private static final class TreePathStringFunction implements Function<TreePath, String> {
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
    return createDefaultRenderer(false);
  }

  static TreeCellRenderer createDefaultRenderer(boolean showSeparatorAsAction) {
    return new MyTreeCellRenderer(showSeparatorAsAction);
  }

  private static final class MyTreeCellRenderer extends ColoredTreeCellRenderer {

    private final boolean myShowSeparatorAsAction;

    private MyTreeCellRenderer(boolean showSeparatorAsAction) { myShowSeparatorAsAction = showSeparatorAsAction; }

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
        if (myShowSeparatorAsAction && (userObject instanceof Separator)) {
          setIcon(AllIcons.General.SeparatorH);
          append(ActionsBundle.message("action.separator"));
        }
        else {
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
        }
        setForeground(UIUtil.getTreeForeground(selected, hasFocus));
      }
    }
  }

  static @Nullable String getActionId(DefaultMutableTreeNode node) {
    Object obj = node.getUserObject();
    if (obj instanceof String actionId) return actionId;
    if (obj instanceof Group group) return group.getId();
    if (obj instanceof Pair<?, ?> pair) {
      Object first = pair.first;
      return first instanceof Group group ? group.getId() : (String)first;
    }
    return null;
  }

  static @NotNull Pair<@Nullable String, @Nullable Icon> getActionIdAndIcon(@NotNull DefaultMutableTreeNode node) {
    Object userObj = node.getUserObject();
    if (userObj instanceof String actionId) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action != null) {
        return new Pair<>(actionId, action.getTemplatePresentation().getIcon());
      }
    }
    else if (userObj instanceof Group group) {
      return new Pair<>(group.getId(), group.getIcon());
    }
    else if (userObj instanceof Pair<?, ?> pair) {
      Object first = pair.first;
      String actionId = first instanceof Group group ? group.getId() : (String)first;
      return new Pair<>(actionId, (Icon)pair.second);
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
      Icon toSet = CustomActionsSchemaKt.getOriginalIconFrom(reuseFrom);
      Icon defaultIcon = CustomActionsSchemaKt.getOriginalIconFrom(action);
      node.setUserObject(Pair.create(value, toSet));
      schema.addIconCustomization(actionId, toSet != defaultIcon ? path : null);
    }
    else {
      Icon icon;
      try {
        icon = CustomActionsSchemaKt.loadCustomIcon(path);
      }
      catch (Throwable t) {
        Logger.getInstance(CustomizableActionsPanel.class)
          .warn(String.format("Failed to load icon with path '%s' and set it to action '%s'", path, actionId), t);
        return false;
      }
      node.setUserObject(Pair.create(value, icon));
      schema.addIconCustomization(actionId, path);
    }
    return true;
  }

  private static void changePathInActionsTree(@NotNull JTree tree, @NotNull ActionUrl url) {
    int actionType = url.getActionType();
    if (actionType == ADDED) {
      addPathToActionsTree(tree, url);
    }
    else if (actionType == DELETED) {
      removePathFromActionsTree(tree, url);
    }
    else if (actionType == MOVE) {
      movePathInActionsTree(tree, url);
    }
  }

  private static @Nullable DefaultMutableTreeNode addPathToActionsTree(@NotNull JTree tree, @NotNull ActionUrl url) {
    TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
    int absolutePosition = url.getAbsolutePosition();
    if (node.getChildCount() >= absolutePosition && absolutePosition >= 0) {
      DefaultMutableTreeNode newNode;
      if (url.getComponent() instanceof Group o) {
        newNode = ActionsTreeUtil.createNode(o);
      }
      else {
        newNode = new DefaultMutableTreeNode(url.getComponent());
      }
      node.insert(newNode, absolutePosition);
      return newNode;
    }
    return null;
  }

  private static void removePathFromActionsTree(@NotNull JTree tree, @NotNull ActionUrl url) {
    Object component = url.getComponent();
    if (component == null) return;
    TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath == null) return;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
    int absolutePosition = url.getAbsolutePosition();
    if (node.getChildCount() > absolutePosition && absolutePosition >= 0) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(absolutePosition);
      Object userObj = child.getUserObject();
      if (component.equals(userObj instanceof Pair<?, ?> pair ? pair.first : userObj)) {
        node.remove(child);
      }
    }
  }

  private static void movePathInActionsTree(@NotNull JTree tree, @NotNull ActionUrl url) {
    TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    Object pathComponent = treePath == null ? null : treePath.getLastPathComponent();
    DefaultMutableTreeNode parent = pathComponent instanceof DefaultMutableTreeNode o ? o : null;
    if (parent == null) return;
    int absolutePosition = url.getAbsolutePosition();
    int initialPosition = url.getInitialPosition();
    Object component = url.getComponent();
    if (parent.getChildCount() > absolutePosition && absolutePosition >= 0) {
      if (parent.getChildCount() > initialPosition && initialPosition >= 0) {
        final DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getChildAt(initialPosition);
        Object userObj = child.getUserObject();
        if (component != null && component.equals(userObj instanceof Pair<?, ?> pair ? pair.first : userObj)) {
          parent.remove(child);
          parent.insert(child, absolutePosition);
        }
      }
    }
  }

  private static @NotNull ArrayList<String> getGroupPath(final TreePath treePath, boolean includeSelf) {
    ArrayList<String> result = new ArrayList<>();
    int length = treePath.getPath().length - (includeSelf ? 0 : 1);
    for (int i = 0; i < length; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group) {
        result.add(((Group)o).getName());
      }
    }
    return result;
  }

  private final class EditIconDialog extends DialogWrapper {
    private final DefaultMutableTreeNode myNode;
    private final boolean isNodeInsideMenu;
    private BrowseIconsComboBox myComboBox;

    private EditIconDialog(TreePath path) {
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
          CustomActionsSchema.getInstance().setCustomizationSchemaForCurrentProjects();
        }
      }
      super.doOKAction();
    }
  }


  private abstract static class TreeSelectionAction extends DumbAwareAction {
    private TreeSelectionAction(@NotNull Supplier<String> text) {
      super(text);
    }

    private TreeSelectionAction(@NotNull Supplier<String> text, @Nullable Icon icon) {
      super(text, icon);
    }

    private TreeSelectionAction(@NotNull Supplier<String> text, @NotNull Supplier<String> description, @Nullable Icon icon) {
      super(text, description, icon);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(true);
      TreePath[] selectionPaths = e.getData(SELECTION);
      if (selectionPaths == null) {
        e.getPresentation().setEnabled(false);
        return;
      }
      for (TreePath path : selectionPaths) {
        if (isNotApplicableForPath(path)) {
          e.getPresentation().setEnabled(false);
          return;
        }
      }
    }

    protected boolean isNotApplicableForPath(@NotNull TreePath path) {
      return !getCustomizationRoots().isUnderCustomizationRoot(path);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }

    protected static boolean isSingleSelection(@NotNull AnActionEvent e) {
      final TreePath[] selectionPaths = e.getData(SELECTION);
      return selectionPaths != null && selectionPaths.length == 1;
    }
  }

  private abstract class AddActionActionBase extends TreeSelectionAction {
    private AddActionActionBase(@NotNull Supplier<String> text) {
      super(text);
    }

    private AddActionActionBase(@NotNull Supplier<String> text, @Nullable Icon icon) {
      super(text, icon);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      TreePath selectionPath = e.getData(LEAD_SELECTION);
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
              if (newNode != null && action instanceof String actionId) {
                String path = mySelectedSchema.getIconPath(actionId);
                if (path.isEmpty()) {
                  path = actionId;
                }
                Icon icon = CustomActionsSchemaKt.getIconForPath(s -> ActionManager.getInstance().getAction(s), path);
                newNode.setUserObject(new Pair<>(actionId, icon));
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
        e.getPresentation().setEnabled(isSingleSelection(e));
      }
    }

    @Override
    protected boolean isNotApplicableForPath(@NotNull TreePath path) {
      return getCustomizationRoots().isReadOnlyGroup(path);
    }
  }

  private final class AddActionActionTreeSelectionAction extends AddActionActionBase implements CustomComponentAction {
    private AddActionActionTreeSelectionAction() {
      super(IdeBundle.messagePointer("group.customizations.add.action.button"));
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      JButton button = new JButton(presentation.getText()) {
        @Override
        public Dimension getPreferredSize() {
          Dimension size = super.getPreferredSize();
          if (myPreferredHeightProvider != null) size.height = myPreferredHeightProvider.compute();
          return size;
        }
      };

      button.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          performAction(button, place, presentation);
        }
      });

      return button;
    }

    @Override
    public void updateCustomComponent(@NotNull JComponent component, @NotNull Presentation presentation) {
      component.setEnabled(presentation.isEnabled());
    }

    void performAction(JComponent component, String place, Presentation presentation) {
      DataContext dataContext = ActionToolbar.getDataContextFor(component);
      AnActionEvent event = AnActionEvent.createFromInputEvent(null, place, presentation, dataContext);
      ActionUtil.performAction(this, event);
    }
  }

  private final class AddActionBelowSelectionAction extends AddActionActionBase {
    private AddActionBelowSelectionAction() {
      super(IdeBundle.messagePointer("group.customizations.add.action.below"), AllIcons.General.Add);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      var leadSelection = e.getData(LEAD_SELECTION);
      if (leadSelection == null) {
        e.getPresentation().setEnabled(false);
        return;
      }
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelection.getLastPathComponent();
      boolean isGroup = CustomizationUtil.getGroupForNode(node) != null;

      if (isGroup) {
        e.getPresentation().setText(IdeBundle.messagePointer("group.customizations.add.action.group"));
      }
      else {
        e.getPresentation().setText(IdeBundle.messagePointer("group.customizations.add.action.below"));
      }
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
      super(IdeBundle.messagePointer("button.add.separator"), AllIcons.General.SeparatorH);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      final List<TreePath> expandedPaths = TreeUtil.collectExpandedPaths(myActionsTree);
      final TreePath selectionPath = e.getData(LEAD_SELECTION);
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
        e.getPresentation().setEnabled(isSingleSelection(e));
      }
    }
  }

  private final class MyActionsTreeModel extends DefaultTreeModel implements EditableModel, RowsDnDSupport.RefinedDropSupport {
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
      super(IdeBundle.messagePointer("button.remove"), Presentation.NULL_STRING, AllIcons.Actions.GC);
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
      removePaths(e.getData(SELECTION));
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
      final TreePath selectionPath = e.getData(LEAD_SELECTION);
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
        var leadSelection = e.getData(LEAD_SELECTION);
        if (leadSelection == null) {
          e.getPresentation().setEnabled(false);
          return;
        }
        final DefaultMutableTreeNode node = (DefaultMutableTreeNode)leadSelection.getLastPathComponent();
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
      final TreePath[] selectionPath = e.getData(SELECTION);
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
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(e, -1));
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
      final TreePath[] selectionPath = e.getData(SELECTION);
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
      e.getPresentation().setEnabled(e.getPresentation().isEnabled() && isMoveSupported(e, 1));
    }
  }

  private final class RestoreSelectionAction extends DumbAwareAction {
    private RestoreSelectionAction() {
      super(IdeBundle.messagePointer("button.restore.selected.groups"));
    }

    private Pair<TreeSet<String>, List<ActionUrl>> findActionsUnderSelection(@NotNull AnActionEvent e) {
      ArrayList<ActionUrl> actions = new ArrayList<>();
      TreeSet<String> selectedNames = new TreeSet<>();
      TreePath[] selectionPaths = e.getData(SELECTION);
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
      otherActions.removeAll(findActionsUnderSelection(e).second);
      mySelectedSchema.copyFrom(new CustomActionsSchema(null));
      for (ActionUrl otherAction : otherActions) {
        mySelectedSchema.addAction(otherAction);
      }
      final List<TreePath> treePaths = TreeUtil.collectExpandedPaths(myActionsTree);
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
      restorePathsAfterTreeOptimization(treePaths);
      onModified();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Pair<TreeSet<String>, List<ActionUrl>> selection = findActionsUnderSelection(e);
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
      mySelectedSchema.copyFrom(new CustomActionsSchema(null));
      patchActionsTreeCorrespondingToSchema((DefaultMutableTreeNode)myActionsTree.getModel().getRoot());
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      e.getPresentation().setEnabled(mySelectedSchema.isModified(new CustomActionsSchema(null)));
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.BGT;
    }
  }
}
