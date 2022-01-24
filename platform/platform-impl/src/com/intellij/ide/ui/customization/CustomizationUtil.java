// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization;

import com.intellij.icons.AllIcons;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.*;
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
import com.intellij.openapi.util.Comparing;
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
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.tree.TreeUtil;
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
import java.awt.event.ActionEvent;
import java.awt.event.MouseListener;
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

  public static ActionGroup correctActionGroup(final ActionGroup group,
                                               final CustomActionsSchema schema,
                                               final String defaultGroupName,
                                               final String rootGroupName,
                                               boolean force) {
    if (!force && !schema.isCorrectActionGroup(group, defaultGroupName)) {
      return group;
    }

    String text = group.getTemplatePresentation().getText();
    final int mnemonic = group.getTemplatePresentation().getMnemonic();
    if (text != null) {
      for (int i = 0; i < text.length(); i++) {
        if (Character.toUpperCase(text.charAt(i)) == mnemonic) {
          text = text.replaceFirst(String.valueOf(text.charAt(i)), "_" + text.charAt(i));
          break;
        }
      }
    }

    return new CustomisedActionGroup(text, group, schema, defaultGroupName, rootGroupName);
  }


  static AnAction [] getReordableChildren(ActionGroup group,
                                          CustomActionsSchema schema,
                                          String defaultGroupName,
                                          String rootGroupName,
                                          AnActionEvent e) {
    String text = group.getTemplatePresentation().getText();
    ActionManager actionManager = ActionManager.getInstance();
    final ArrayList<AnAction> reorderedChildren = new ArrayList<>();
    ContainerUtil.addAll(reorderedChildren, group.getChildren(e));
    final List<ActionUrl> actions = schema.getActions();
    for (ActionUrl actionUrl : actions) {
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
      if (reorderedChildren.get(i) instanceof ActionGroup) {
        final ActionGroup groupToCorrect = (ActionGroup)reorderedChildren.get(i);
        final AnAction correctedAction = correctActionGroup(groupToCorrect, schema, "", rootGroupName, false);
        reorderedChildren.set(i, correctedAction);
      }
    }

    return reorderedChildren.toArray(AnAction.EMPTY_ARRAY);
  }

  public static void optimizeSchema(final JTree tree, final CustomActionsSchema schema) {
    //noinspection HardCodedStringLiteral
    Group rootGroup = new Group("root", null, null);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    root.removeAllChildren();
    schema.fillActionGroups(root);
    final JTree defaultTree = new Tree(new DefaultTreeModel(root));

    final List<ActionUrl> actions = new ArrayList<>();
    TreeUtil.traverseDepth((TreeNode)tree.getModel().getRoot(), node -> {
      DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
      Object userObject = treeNode.getUserObject();
      if (treeNode.isLeaf() && !(userObject instanceof Group)) {
        return true;
      }
      ActionUrl url = getActionUrl(new TreePath(treeNode.getPath()), 0);
      String groupName = ((Group)userObject).getName();
      url.getGroupPath().add(groupName);
      final TreePath treePath = getTreePath(defaultTree, url);
      if (treePath != null) {
        final DefaultMutableTreeNode visited = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        final ActionUrl[] defaultUserObjects = getChildUserObjects(visited, url);
        final ActionUrl[] currentUserObjects = getChildUserObjects(treeNode, url);
        computeDiff(defaultUserObjects, currentUserObjects, actions);
      } else {
        //customizations at the new place
        url.getGroupPath().remove(url.getParentGroup());
        if (actions.contains(url)){
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

  public static TreePath getPathByUserObjects(JTree tree, TreePath treePath){
    List<String>  path = new ArrayList<>();
    for (int i = 0; i < treePath.getPath().length; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group) {
        path.add(((Group)o).getName());
      }
    }
    return getTreePath(0, path, tree.getModel().getRoot());
  }

  public static ActionUrl getActionUrl(final TreePath treePath, int actionType) {
    ActionUrl url = new ActionUrl();
    for (int i = 0; i < treePath.getPath().length - 1; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group) {
        url.getGroupPath().add(((Group)o).getName());
      }

    }

    final DefaultMutableTreeNode component = ((DefaultMutableTreeNode)treePath.getLastPathComponent());
    url.setComponent(component.getUserObject());
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
    if (!(root instanceof DefaultMutableTreeNode)) return null;

    final DefaultMutableTreeNode treeNode = ((DefaultMutableTreeNode)root);

    final Object userObject = treeNode.getUserObject();

    final String pathElement;
    if (path.size() > positionInPath) {
      pathElement = path.get(positionInPath);
    }
    else {
      return null;
    }

    if (pathElement == null) return null;

    if (!(userObject instanceof Group)) return null;

    if (!pathElement.equals(((Group)userObject).getName())) return null;


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
    PopupHandler popupHandler = PopupHandler.installPopupMenu(
      component, new ActionGroup() {
        @Override
        public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
          ActionGroup group = actionGroupSupplier.get();
          return group == null ? EMPTY_ARRAY : group.getChildren(e);
        }
      }, place);
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
   *   <li>{@link Separator}</li>
   *   <li>{@link QuickList}</li>
   *   </ul>
   * </p>
   *
   * @throws IllegalArgumentException if {@code obj} has wrong type
   */
  public static void acceptObjectIconAndText(@Nullable Object obj, BiConsumer<@Nls @NotNull String, @Nullable Icon> consumer) {
    @NotNull String text;
    Icon icon = null;
    if (obj instanceof Group) {
      Group group = (Group)obj;
      String name = group.getName();
      @NlsSafe String id = group.getId();
      text = name != null ? name : ObjectUtils.notNull(id, IdeBundle.message("action.group.name.unnamed.group"));
      icon = ObjectUtils.notNull(group.getIcon(), AllIcons.Nodes.Folder);
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
    }
    else if (obj instanceof Pair) {
      String actionId = (String)((Pair<?, ?>)obj).first;
      AnAction action = ActionManager.getInstance().getAction(actionId);
      var t = action != null ? action.getTemplatePresentation().getText() : null;
      text = StringUtil.isNotEmpty(t) ? t : actionId;
      icon = (Icon)((Pair<?, ?>)obj).second;
    }
    else if (obj instanceof Separator) {
      text = "-------------";
    }
    else if (obj instanceof QuickList) {
      text = ((QuickList)obj).getDisplayName();
    }
    else if (obj == null) {
      //noinspection HardCodedStringLiteral
      text = "null";
    }
    else {
      throw new IllegalArgumentException("unknown obj: " + obj);
    }
    consumer.accept(text, icon);
  }

  /**
   * Returns {@code schema} actions for the group with {@code groupId}.
   *
   * @param groupId action group ID
   * @param schema schema where actions are
   * @return list of objects
   *
   * @see CustomizationUtil#acceptObjectIconAndText(Object, BiConsumer)
   *
   * @throws IllegalStateException if group is not found
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
   * @param schema schema where group is
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
    String groupID = ActionManager.getInstance().getId(actionGroup instanceof CustomisedActionGroup
                                                       ? ((CustomisedActionGroup)actionGroup).getOrigin() : actionGroup);
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
                                   protected @NotNull Action getOKAction() {
                                     return new AbstractAction(ActionsBundle.message("apply.toolbar.customization")) {
                                       @Override
                                       public void actionPerformed(ActionEvent e) {
                                         try {
                                           panel.apply();
                                         }
                                         catch (ConfigurationException ex) {
                                           LOG.error(ex);
                                         }
                                         close(0);
                                       }
                                     };
                                   }
                                 };
                                 dialogWrapper.show();
                               }));
      return PopupHandler.installPopupMenu(toolbar.getComponent(), customizationGroup, toolbar.getPlace(), new PopupMenuListenerAdapter() {
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
  public static String getGroupName(AnAction action, String groupID) {
    String templateText = action.getTemplateText();
    return Strings.isEmpty(templateText) ? CustomActionsSchema.getInstance().getDisplayName(groupID) : templateText;
  }

  private static class ToolbarCustomizableActionsPanel extends CustomizableActionsPanel {
    @NotNull private final String myGroupID;
    @NotNull private final String myName;

    private ToolbarCustomizableActionsPanel(@NotNull String groupID, @NotNull String name) {
      myGroupID = groupID;
      myName = name;
    }

    @Override
    protected @NotNull ActionGroup getRestoreGroup() {
      return new DefaultActionGroup(new DumbAwareAction(IdeBundle.messagePointer("button.restore.last.state")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          reset();
        }
      }, new DumbAwareAction(IdeBundle.messagePointer("button.restore.defaults")) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          fillTreeFromActions((DefaultMutableTreeNode)myActionsTree.getModel().getRoot(),
                              (ActionGroup)ActionManager.getInstance().getAction(myGroupID));
          TreeUtil.ensureSelection(myActionsTree);
          try {
            apply();
          }
          catch (ConfigurationException ex) {
            LOG.error(ex);
          }
        }
      }){
        {
          getTemplatePresentation().setPopupGroup(true);
          getTemplatePresentation().setIcon(AllIcons.Actions.Rollback);
        }
      };
    }

    JComponent getPreferredFocusedComponent() {
      return myActionsTree;
    }

    @Override
    public void reset() {
      super.reset();
      new DefaultTreeExpander(myActionsTree).expandAll();
    }

    @Override
    protected void patchActionsTreeCorrespondingToSchema(DefaultMutableTreeNode root) {
      if (myGroupID == null) return;
      fillTreeFromActions(root, (ActionGroup)ActionManager.getInstance().getAction(myGroupID));
    }

    private void fillTreeFromActions(@Nullable DefaultMutableTreeNode root, @Nullable ActionGroup actionGroup) {
      List<TreePath> selectedPaths = TreeUtil.collectSelectedPaths(myActionsTree);
      if (mySelectedSchema != null && actionGroup != null && root != null) {
        root.removeAllChildren();
        root.add(ActionsTreeUtil.createNode(
          ActionsTreeUtil.createCorrectedGroup(actionGroup, myName, new ArrayList<>(), mySelectedSchema.getActions())));
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
          if (Comparing.equal(userObjectString, actionID)) {
            TreeUtil.selectPath(myActionsTree, path);
            return Action.INTERRUPT;
          }
          return Action.CONTINUE;
        }
      });
    }
  }
}
