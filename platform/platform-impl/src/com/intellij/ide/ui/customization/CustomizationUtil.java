// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.ui.ToolbarSettings;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.actionSystem.impl.PopupMenuPreloader;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.JBPopupMenu;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.Strings;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.tree.TreeVisitor;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.TreeTraversal;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

public final class CustomizationUtil {
  private static final Logger LOG = Logger.getInstance(CustomizationUtil.class);

  private CustomizationUtil() {
  }

  public static @Nullable Icon getOriginalIconFrom(@NotNull AnAction reuseFrom) {
    Presentation presentation = reuseFrom.getTemplatePresentation();
    Icon original = presentation.getClientProperty(CustomActionsSchema.PROP_ORIGINAL_ICON);
    if (original != null) return original;
    return presentation.getIcon();
  }

  @Nullable
  public static Icon getIconForPath(@NotNull ActionManager actionManager, @Nullable String iconPath) {
    if (iconPath == null) {
      return null;
    }
    AnAction reuseFrom = actionManager.getAction(iconPath);
    if (reuseFrom != null) {
      return getOriginalIconFrom(reuseFrom);
    }
    else {
      try {
        return CustomActionsSchema.loadCustomIcon(iconPath);
      }
      catch (IOException e) {
        LOG.info(e.getMessage());
        return null;
      }
    }
  }



  public static ActionGroup correctActionGroup(final ActionGroup group,
                                               final CustomActionsSchema schema,
                                               final String defaultGroupName,
                                               final String rootGroupName,
                                               boolean force) {
    if (!force && !schema.isCorrectActionGroup(group, defaultGroupName)) {
      return group;
    }

    String text = group.getTemplatePresentation().getText(true);
    if (text != null) {
      int index = group.getTemplatePresentation().getDisplayedMnemonicIndex();
      if (0 <= index && index <= text.length()) {
        text = text.substring(0, index) + UIUtil.MNEMONIC + text.substring(index);
      }
    }

    ActionGroup correctedGroup = new CustomisedActionGroup(text, group, schema, defaultGroupName, rootGroupName);
    String groupId = ActionManager.getInstance().getId(group);
    for (ActionUrl actionUrl : schema.getActions()) {
      Group g1 = actionUrl.getComponent() instanceof Group g ? g : null;
      if (g1 != null && Objects.equals(g1.getId(), groupId)) {
        if (g1.isForceShowAsPopup()) correctedGroup.setPopup(true);
        break;
      }
    }
    return correctedGroup;
  }


  static AnAction[] getReordableChildren(ActionGroup group,
                                         CustomActionsSchema schema,
                                         String defaultGroupName,
                                         String rootGroupName,
                                         AnActionEvent e) {
    String text = group.getTemplatePresentation().getText();
    ActionManager actionManager = ActionManager.getInstance();
    final ArrayList<AnAction> reorderedChildren = new ArrayList<>();
    ContainerUtil.addAll(reorderedChildren, group.getChildren(e));
    for (ActionUrl actionUrl : schema.getActions()) {
      if (actionUrl.getParentGroup() == null) continue;
      if ((actionUrl.getParentGroup().equals(text) ||
           actionUrl.getParentGroup().equals(defaultGroupName) ||
           actionUrl.getParentGroup().equals(actionManager.getId(group)) && actionUrl.getRootGroup().equals(rootGroupName))) {
        AnAction componentAction = actionUrl.getComponentAction();
        if (componentAction != null) {
          if (actionUrl.getActionType() == ActionUrl.ADDED) {
            if (componentAction == group) {
              LOG.error("Attempt to add group to itself; group ID=" + actionManager.getId(group));
              continue;
            }
            if (reorderedChildren.size() > actionUrl.getAbsolutePosition()) {
              reorderedChildren.add(actionUrl.getAbsolutePosition(), componentAction);
            }
            else {
              reorderedChildren.add(componentAction);
            }
          }
          else if (actionUrl.getActionType() == ActionUrl.DELETED && reorderedChildren.size() > actionUrl.getAbsolutePosition()) {
            final AnAction anAction = reorderedChildren.get(actionUrl.getAbsolutePosition());
            if (anAction.getTemplatePresentation().getText() == null
                ? (componentAction.getTemplatePresentation().getText() != null &&
                   componentAction.getTemplatePresentation().getText().length() > 0)
                : !anAction.getTemplatePresentation().getText().equals(componentAction.getTemplatePresentation().getText())) {
              continue;
            }
            reorderedChildren.remove(actionUrl.getAbsolutePosition());
          }
        }
      }
    }
    for (int i = 0; i < reorderedChildren.size(); i++) {
      if (reorderedChildren.get(i) instanceof ActionGroup groupToCorrect) {
        final AnAction correctedAction = correctActionGroup(groupToCorrect, schema, "", rootGroupName, false);
        reorderedChildren.set(i, correctedAction);
      }
    }

    return reorderedChildren.toArray(AnAction.EMPTY_ARRAY);
  }

  public static void optimizeSchema(final JTree tree, final CustomActionsSchema schema) {
    //noinspection HardCodedStringLiteral
    @SuppressWarnings("DialogTitleCapitalization")
    Group rootGroup = new Group("root", null, null);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    root.removeAllChildren();
    schema.fillActionGroups(root);
    final JTree defaultTree = new Tree(new DefaultTreeModel(root));

    final List<ActionUrl> actions = new ArrayList<>();
    TreeUtil.treeNodeTraverser((TreeNode)tree.getModel().getRoot()).traverse(TreeTraversal.PRE_ORDER_DFS).processEach(node -> {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
      Group group = getGroupForNode(treeNode);
      if (treeNode.isLeaf() && group == null) {
        return true;
      }
      ActionUrl url = getActionUrl(new TreePath(treeNode.getPath()), 0);
      String groupName = group.getName();
      url.getGroupPath().add(groupName);
      final TreePath treePath = getTreePath(defaultTree, url);
      if (treePath != null) {
        final DefaultMutableTreeNode visited = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        final ActionUrl[] defaultUserObjects = getChildUserObjects(visited, url);
        final ActionUrl[] currentUserObjects = getChildUserObjects(treeNode, url);
        computeDiff(defaultUserObjects, currentUserObjects, actions);
      }
      else {
        //customizations at the new place
        url.getGroupPath().remove(url.getParentGroup());
        if (actions.contains(url)) {
          url.getGroupPath().add(groupName);
          actions.addAll(schema.getChildActions(url));
        }
      }
      return true;
    });
    schema.setActions(actions);
  }

  private static void computeDiff(final ActionUrl[] defaultUserObjects,
                                  final ActionUrl[] currentUserObjects,
                                  @NotNull List<? super ActionUrl> actions) {
    Diff.Change change = null;
    try {
      change = Diff.buildChanges(defaultUserObjects, currentUserObjects);
    }
    catch (FilesTooBigForDiffException e) {
      LOG.info(e);
    }
    while (change != null) {
      for (int i = 0; i < change.deleted; i++) {
        final int idx = change.line0 + i;
        ActionUrl currentUserObject = defaultUserObjects[idx];
        currentUserObject.setActionType(ActionUrl.DELETED);
        currentUserObject.setAbsolutePosition(idx);
        actions.add(currentUserObject);
      }
      for (int i = 0; i < change.inserted; i++) {
        final int idx = change.line1 + i;
        ActionUrl currentUserObject = currentUserObjects[idx];
        currentUserObject.setActionType(ActionUrl.ADDED);
        currentUserObject.setAbsolutePosition(idx);
        actions.add(currentUserObject);
      }
      change = change.link;
    }
  }

  public static TreePath getPathByUserObjects(JTree tree, TreePath treePath) {
    List<String> path = new ArrayList<>();
    for (int i = 0; i < treePath.getPath().length; i++) {
      Group group = getGroupForNode((DefaultMutableTreeNode)treePath.getPath()[i]);
      if (group != null) {
        path.add(group.getName());
      }
    }
    return getTreePath(0, path, tree.getModel().getRoot());
  }

  public static ActionUrl getActionUrl(final TreePath treePath,
                                       @MagicConstant(intValues = {ActionUrl.ADDED, ActionUrl.DELETED, ActionUrl.MOVE}) int actionType) {
    ActionUrl url = new ActionUrl();
    for (int i = 0; i < treePath.getPath().length - 1; i++) {
      Group group = getGroupForNode((DefaultMutableTreeNode)treePath.getPath()[i]);
      if (group != null) {
        url.getGroupPath().add(group.getName());
      }
    }

    final DefaultMutableTreeNode component = ((DefaultMutableTreeNode)treePath.getLastPathComponent());
    Object userObj = component.getUserObject();
    url.setComponent(userObj instanceof Pair<?, ?> pair ? pair.first : userObj);
    final TreeNode parent = component.getParent();
    url.setAbsolutePosition(parent != null ? parent.getIndex(component) : 0);
    url.setActionType(actionType);
    return url;
  }


  public static TreePath getTreePath(JTree tree, ActionUrl url) {
    return getTreePath(0, url.getGroupPath(), tree.getModel().getRoot());
  }

  @Nullable
  private static TreePath getTreePath(final int positionInPath, final List<String> path, final Object root) {
    if (!(root instanceof DefaultMutableTreeNode treeNode)) return null;

    final String pathElement;
    if (path.size() > positionInPath) {
      pathElement = path.get(positionInPath);
    }
    else {
      return null;
    }

    if (pathElement == null) return null;

    final Group group = getGroupForNode(treeNode);
    if (group == null) return null;

    if (!pathElement.equals(group.getName())) return null;


    TreePath currentPath = new TreePath(treeNode.getPath());

    if (positionInPath == path.size() - 1) {
      return currentPath;
    }

    for (int j = 0; j < treeNode.getChildCount(); j++) {
      final TreeNode child = treeNode.getChildAt(j);
      currentPath = getTreePath(positionInPath + 1, path, child);
      if (currentPath != null) {
        break;
      }
    }

    return currentPath;
  }

  /**
   * @return group if user object of provided node is {@link Group}, or "{@link Group}, {@link Icon}" pair
   */
  @Nullable
  public static Group getGroupForNode(@NotNull DefaultMutableTreeNode node) {
    Object userObj = node.getUserObject();
    Object value = userObj instanceof Pair<?, ?> pair ? pair.first : userObj;
    return value instanceof Group group ? group : null;
  }

  private static ActionUrl[] getChildUserObjects(DefaultMutableTreeNode node, ActionUrl parent) {
    ArrayList<ActionUrl> result = new ArrayList<>();
    ArrayList<String> groupPath = new ArrayList<>(parent.getGroupPath());
    for (int i = 0; i < node.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
      ActionUrl url = new ActionUrl();
      url.setGroupPath(groupPath);
      final Object userObject = child.getUserObject();
      url.setComponent(userObject instanceof Pair ? ((Pair<?, ?>)userObject).first : userObject);
      result.add(url);
    }
    return result.toArray(new ActionUrl[0]);
  }

  @NotNull
  public static MouseListener installPopupHandler(@NotNull JComponent component, @NotNull String groupId, @NotNull String place) {
    Supplier<ActionGroup> actionGroupSupplier = () -> (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(groupId);
    PopupHandler popupHandler = PopupHandler.installPopupMenu(component, new PopupComputableActionGroup(actionGroupSupplier), place);
    PopupMenuPreloader.install(component, place, popupHandler, actionGroupSupplier);
    return popupHandler;
  }

  /**
   * Retrieve text and icon from the object and pass them to {@code consumer}.
   * <p>This types of object can be processed:
   *   <ul>
   *   <li>{@link Group}</li>
   *   <li>{@link String} (action ID)</li>
   *   <li>{@link Pair}&lt;String actionId, Icon customIcon&gt;</li>
   *   <li>{@link Pair}&lt;Group group, Icon customIcon&gt;</li>
   *   <li>{@link Separator}</li>
   *   <li>{@link QuickList}</li>
   *   </ul>
   * </p>
   *
   * @throws IllegalArgumentException if {@code obj} has wrong type
   */
  public static void acceptObjectIconAndText(@Nullable Object obj, @NotNull CustomPresentationConsumer consumer) {
    @NotNull String text;
    @Nullable String description = null;
    Icon icon = null;
    if (obj instanceof Group) {
      Group group = (Group)obj;
      String name = group.getName();
      @NlsSafe String id = group.getId();
      text = name != null ? name : ObjectUtils.notNull(id, IdeBundle.message("action.group.name.unnamed.group"));
      icon = group.getIcon();
      if (UISettings.getInstance().getShowInplaceCommentsInternal()) {
        description = id;
      }
    }
    else if (obj instanceof String) {
      String actionId = (String)obj;
      AnAction action = ActionManager.getInstance().getAction(actionId);
      String name = action != null ? action.getTemplatePresentation().getText() : null;
      text = !StringUtil.isEmptyOrSpaces(name) ? name : actionId;
      if (action != null) {
        Icon actionIcon = action.getTemplatePresentation().getIcon();
        if (actionIcon != null) {
          icon = actionIcon;
        }
      }
      if (UISettings.getInstance().getShowInplaceCommentsInternal()) {
        description = actionId;
      }
    }
    else if (obj instanceof Pair<?, ?> pair) {
      Object actionIdOrGroup = pair.first;
      String actionId = actionIdOrGroup instanceof Group group ? group.getId() : (String)actionIdOrGroup;
      AnAction action = actionId == null ? null : ActionManager.getInstance().getAction(actionId);
      var t = action != null ? action.getTemplatePresentation().getText() : null;
      text = StringUtil.isNotEmpty(t) ? t : ObjectUtils.notNull(actionId, IdeBundle.message("action.group.name.unnamed.group"));
      Icon actionIcon = (Icon)pair.second;
      if (actionIcon == null && action != null) {
        actionIcon = action.getTemplatePresentation().getClientProperty(CustomActionsSchema.PROP_ORIGINAL_ICON);
      }
      icon = actionIcon;
      if (UISettings.getInstance().getShowInplaceCommentsInternal()) {
        description = actionId;
      }
    }
    else if (obj instanceof Separator) {
      text = "-------------";
    }
    else if (obj instanceof QuickList quickList) {
      text = quickList.getDisplayName();
      if (UISettings.getInstance().getShowInplaceCommentsInternal()) {
        description = quickList.getActionId();
      }
    }
    else if (obj == null) {
      //noinspection HardCodedStringLiteral
      text = "null";
    }
    else {
      throw new IllegalArgumentException("unknown obj: " + obj);
    }
    consumer.accept(text, description, icon);
  }

  /**
   * Returns {@code schema} actions for the group with {@code groupId}.
   *
   * @param groupId action group ID
   * @param schema  schema where actions are
   * @return list of objects
   * @throws IllegalStateException if group is not found
   * @see CustomizationUtil#acceptObjectIconAndText(Object, BiConsumer)
   */
  public static @NotNull List<Object> getGroupActions(@NotNull String groupId, @NotNull CustomActionsSchema schema) {
    var group = getGroup(groupId, schema);
    if (group == null) {
      throw new IllegalStateException("ActionGroup[" + groupId + "] is not found");
    }
    return group.getChildren();
  }

  /**
   * Returns {@link  Group} for specified {@code schema}.
   *
   * @param groupId action group ID
   * @param schema  schema where group is
   * @return {@link Group} or {@code null} if group isn't found
   */
  public static @Nullable Group getGroup(@NotNull String groupId, @NotNull CustomActionsSchema schema) {
    var group = ObjectUtils.tryCast(schema.getCorrectedAction(groupId), ActionGroup.class);
    if (group == null) {
      return null;
    }
    @NlsSafe
    String displayName = schema.getDisplayName(groupId);
    return ActionsTreeUtil.createGroup(group, displayName, null, null, false, action -> true);
  }

  /**
   * Update group with {@code groupId} with {@code actions}.
   *
   * @param actions list of new actions to be set
   * @param groupId target group ID to be updated
   */
  public static void updateActionGroup(@NotNull List<Object> actions, @NotNull String groupId) {
    var defaultActionList = getGroupActions(groupId, new CustomActionsSchema());
    var diff = new ArrayList<ActionUrl>();
    var groupPath = new ArrayList<>(Arrays.asList("root", CustomActionsSchema.getInstance().getDisplayName(groupId)));
    computeDiff(toActionUrls(groupPath, defaultActionList), toActionUrls(groupPath, actions), diff);

    var globalSchema = CustomActionsSchema.getInstance();
    var tmpSchema = new CustomActionsSchema();
    tmpSchema.copyFrom(globalSchema);
    tmpSchema.getActions().removeIf(url -> Objects.equals(groupPath, url.getGroupPath()));
    tmpSchema.getActions().addAll(diff);

    globalSchema.copyFrom(tmpSchema);
    CustomActionsListener.fireSchemaChanged();
  }

  private static ActionUrl @NotNull [] toActionUrls(@NotNull ArrayList<String> groupPath, @NotNull List<Object> objects) {
    return objects.stream().map(o -> new ActionUrl(groupPath, o, 0, -1)).toArray(ActionUrl[]::new);
  }

  @Nullable
  public static PopupHandler installToolbarCustomizationHandler(@NotNull ActionToolbarImpl toolbar) {
    ActionGroup actionGroup = toolbar.getActionGroup();
    String groupID = getGroupID(actionGroup);
    if (groupID == null) {
      return null;
    }
    return installToolbarCustomizationHandler(actionGroup, groupID, toolbar.getComponent(), toolbar.getPlace());
  }

  @Nullable
  public static PopupHandler installToolbarCustomizationHandler(@NotNull ActionGroup actionGroup,
                                                                String groupID, JComponent component, String place) {
    if (groupID != null) {
      final String groupName = getGroupName(actionGroup, groupID);
      if (groupName == null) return null;

      final Ref<Component> popupInvoker = Ref.create();
      DefaultActionGroup customizationGroup = new DefaultActionGroup(
        DumbAwareAction.create(IdeBundle.message("action.customizations.customize.action"),
                               event -> {
                                 Component src = popupInvoker.get();
                                 AnAction targetAction = ObjectUtils.doIfCast(src, ActionButton.class, ActionButton::getAction);
                                 ToolbarCustomizableActionsPanel panel = new ToolbarCustomizableActionsPanel(groupID, groupName);
                                 DialogWrapper dialogWrapper = new DialogWrapper(event.getProject(), true) {
                                   {
                                     setTitle(IdeBundle.message("dialog.title.customize.0", groupName));
                                     init();
                                     setSize(600, 600);
                                   }

                                   @Override
                                   public @Nullable JComponent getPreferredFocusedComponent() {
                                     return panel.getPreferredFocusedComponent();
                                   }

                                   @Override
                                   protected @Nullable JComponent createCenterPanel() {
                                     panel.reset();
                                     String id = ObjectUtils.doIfNotNull(targetAction, ActionManager.getInstance()::getId);
                                     if (id != null) {
                                       panel.selectAction(id);
                                     }
                                     return panel.getPanel();
                                   }

                                   @Override
                                   protected void doOKAction() {
                                     try {
                                       panel.apply();
                                     }
                                     catch (ConfigurationException ex) {
                                       LOG.error(ex);
                                     }
                                     close(OK_EXIT_CODE);
                                   }

                                   @Override
                                   public void doCancelAction() {
                                     panel.reset();
                                     super.doCancelAction();
                                   }
                                 };
                                 dialogWrapper.show();
                               })
      );


        AnAction rollbackAction = ActionManager.getInstance().getAction(ToolbarSettings.ROLLBACK_ACTION_ID);
        if(rollbackAction != null) {
          customizationGroup.add(rollbackAction);
        }

        return PopupHandler.installPopupMenu(component, customizationGroup, place, new PopupMenuListenerAdapter() {
        @Override
        public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
          JBPopupMenu menu = ObjectUtils.tryCast(e.getSource(), JBPopupMenu.class);
          popupInvoker.set(menu != null ? menu.getInvoker() : null);
        }

        @Override
        public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
          ApplicationManager.getApplication().invokeLater(() -> {
            popupInvoker.set(null);
          });
        }
      });
    }
    return null;
  }

  @Nullable
  private static String getGroupID(ActionGroup actionGroup) {
    AnAction actionForId = ActionUtil.getDelegateChainRootAction(actionGroup);
    return ActionManager.getInstance().getId(actionForId);
  }

  @Nls @Nullable
  private static String getGroupName(AnAction action, String groupID) {
    String templateText = action.getTemplateText();
    return Strings.isEmpty(templateText) ? CustomActionsSchema.getInstance().getDisplayName(groupID) : templateText;
  }

  private static class ToolbarCustomizableActionsPanel extends CustomizableActionsPanel {
    @NotNull private final String myGroupID;
    @Nls @NotNull private final String myGroupName;

    private ToolbarCustomizableActionsPanel(@NotNull String groupID, @Nls @NotNull String groupName) {
      myGroupID = groupID;
      myGroupName = groupName;
    }

    @Override
    protected @NotNull ActionGroup getRestoreGroup() {
      return new DefaultActionGroup(new DumbAwareAction(IdeBundle.messagePointer("button.restore.last.state")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          reset();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(mySelectedSchema.isModified(CustomActionsSchema.getInstance()));
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      }, new DumbAwareAction(IdeBundle.messagePointer("button.restore.defaults")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          resetToDefaults();
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          CustomActionsSchema cleanScheme = new CustomActionsSchema();
          updateLocalSchema(cleanScheme);
          e.getPresentation().setEnabled(mySelectedSchema.isModified(cleanScheme));
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      }) {
        {
          getTemplatePresentation().setPopupGroup(true);
          getTemplatePresentation().setIcon(AllIcons.Actions.Rollback);
          getTemplatePresentation().setText(IdeBundle.message("group.customizations.restore.action.group"));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          CustomActionsSchema cleanScheme = new CustomActionsSchema();
          updateLocalSchema(cleanScheme);
          e.getPresentation().setEnabled(mySelectedSchema.isModified(CustomActionsSchema.getInstance()) ||
                                         mySelectedSchema.isModified(cleanScheme));
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }
      };
    }

    JComponent getPreferredFocusedComponent() {
      return myActionsTree;
    }

    @Override
    protected boolean needExpandAll() {
      return true;
    }

    @Override
    protected void updateGlobalSchema() {
      updateLocalSchema(mySelectedSchema);
      super.updateGlobalSchema();
    }

    @Override
    protected void updateLocalSchema(CustomActionsSchema localSchema) {
      CustomActionsSchema.getInstance().getActions().forEach(url -> {
        // Foreign (global) customization shouldn't be lost, so we add them to a scheme with local action group root
        if (!url.getGroupPath().contains(myGroupName)) {
          localSchema.addAction(url.copy());
        }
      });
    }

    @Override
    protected void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
      //noinspection ConstantValue -- can be called from superclass constructor
      if (myGroupID == null) return;
      fillTreeFromActions(root, (ActionGroup)ActionManager.getInstance().getAction(myGroupID));
    }

    private void fillTreeFromActions(@Nullable DefaultMutableTreeNode root, @Nullable ActionGroup actionGroup) {
      List<TreePath> selectedPaths = TreeUtil.collectSelectedPaths(myActionsTree);
      if (mySelectedSchema != null && actionGroup != null && root != null) {
        root.removeAllChildren();
        root.add(ActionsTreeUtil.createNode(
          ActionsTreeUtil.createCorrectedGroup(actionGroup, myGroupName, new ArrayList<>(), mySelectedSchema.getActions())));
        ((DefaultTreeModel)(myActionsTree.getModel())).reload();
        TreeUtil.selectPaths(myActionsTree, selectedPaths);
      }
    }

    private void selectAction(String actionID) {
      TreeUtil.promiseSelect(myActionsTree, new TreeVisitor() {
        @Override
        public @NotNull Action visit(@NotNull TreePath path) {
          String userObjectString = ObjectUtils.doIfCast(path.getLastPathComponent(), DefaultMutableTreeNode.class,
                                                         o -> ObjectUtils.tryCast(o.getUserObject(), String.class));
          if (userObjectString == null) {
            Group group = ObjectUtils.doIfCast(path.getLastPathComponent(), DefaultMutableTreeNode.class,
                                               o -> ObjectUtils.tryCast(o.getUserObject(), Group.class));
            userObjectString = ObjectUtils.doIfNotNull(group, Group::getName);
          }
          if (Objects.equals(userObjectString, actionID)) {
            TreeUtil.selectPath(myActionsTree, path);
            return Action.INTERRUPT;
          }
          return Action.CONTINUE;
        }
      });
    }
  }

  public interface CustomPresentationConsumer {
    void accept(@NotNull @Nls String text, @Nullable @Nls String description, @Nullable Icon icon);
  }

  private static class PopupComputableActionGroup extends ActionGroup implements ActionWithDelegate<ActionGroup> {
    private final Supplier<? extends @Nullable ActionGroup> myActionGroupSupplier;

    PopupComputableActionGroup(Supplier<? extends @Nullable ActionGroup> actionGroupSupplier) {
      myActionGroupSupplier = actionGroupSupplier;
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
      ActionGroup group = myActionGroupSupplier.get();
      return group == null ? EMPTY_ARRAY : group.getChildren(e);
    }

    @Override
    public @NotNull ActionGroup getDelegate() {
      return ObjectUtils.notNull(myActionGroupSupplier.get(), ActionGroup.EMPTY_GROUP);
    }
  }
}
