/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * User: anna
 * Date: Mar 30, 2005
 */
public class CustomizationUtil {
  public static ActionGroup correctActionGroup(final ActionGroup group, final CustomActionsSchema schema, final String defaultGroupName) {
    if (!schema.isCorrectActionGroup(group)){
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

    return new CachedAction(text, group.isPopup(), group, schema, defaultGroupName);
  }


  private static AnAction [] getReordableChildren(ActionGroup group, CustomActionsSchema schema, String defaultGroupName, AnActionEvent e) {
    String text = group.getTemplatePresentation().getText();
    ActionManager actionManager = ActionManager.getInstance();
    final ArrayList<AnAction> reordableChildren = new ArrayList<AnAction>();
    ContainerUtil.addAll(reordableChildren, group.getChildren(e));
    final List<ActionUrl> actions = schema.getActions();
    for (Iterator<ActionUrl> iterator = actions.iterator(); iterator.hasNext();) {
      ActionUrl actionUrl = iterator.next();
      if ((actionUrl.getParentGroup().equals(text) ||
           actionUrl.getParentGroup().equals(defaultGroupName) ||
           actionUrl.getParentGroup().equals(actionManager.getId(group)))) {
        AnAction componentAction = actionUrl.getComponentAction();
        if (componentAction != null) {
          if (actionUrl.getActionType() == ActionUrl.ADDED) {
            if (reordableChildren.size() > actionUrl.getAbsolutePosition()) {
              reordableChildren.add(actionUrl.getAbsolutePosition(), componentAction);
            }
            else {
              reordableChildren.add(componentAction);
            }
          }
          else if (actionUrl.getActionType() == ActionUrl.DELETED && reordableChildren.size() > actionUrl.getAbsolutePosition()) {
            final AnAction anAction = reordableChildren.get(actionUrl.getAbsolutePosition());
            if (anAction.getTemplatePresentation().getText() == null
                ? (componentAction.getTemplatePresentation().getText() != null &&
                   componentAction.getTemplatePresentation().getText().length() > 0)
                : !anAction.getTemplatePresentation().getText().equals(componentAction.getTemplatePresentation().getText())) {
              continue;
            }
            reordableChildren.remove(actionUrl.getAbsolutePosition());
          }
        }
      }
    }
    for (int i = 0; i < reordableChildren.size(); i++) {
      if (reordableChildren.get(i) instanceof ActionGroup) {
        final ActionGroup groupToCorrect = (ActionGroup)reordableChildren.get(i);
        final AnAction correctedAction = correctActionGroup(groupToCorrect, schema, "");
        reordableChildren.set(i, correctedAction);
      }
    }

    return reordableChildren.toArray(new AnAction[reordableChildren.size()]);
  }

  private static class CachedAction extends ActionGroup {
    private boolean myForceUpdate;
    private final ActionGroup myGroup;
    private AnAction[] myChildren;
    private final CustomActionsSchema mySchema;
    private final String myDefaultGroupName;
    public CachedAction(String shortName, boolean popup, final ActionGroup group, CustomActionsSchema schema, String defaultGroupName) {
      super(shortName, popup);
      myGroup = group;
      mySchema = schema;
      myDefaultGroupName = defaultGroupName;
      myForceUpdate = true;
    }

    public AnAction[] getChildren(@Nullable final AnActionEvent e) {
      if (myForceUpdate){
        myChildren = getReordableChildren(myGroup, mySchema, myDefaultGroupName, e);
        myForceUpdate = false;
        return myChildren;
      } else {
        if (!(myGroup instanceof DefaultActionGroup) || myChildren == null){
          myChildren = getReordableChildren(myGroup, mySchema, myDefaultGroupName, e);
        }
        return myChildren;
      }
    }

    public void update(AnActionEvent e) {
      myGroup.update(e);
    }
  }

  public static void optimizeSchema(final JTree tree, final CustomActionsSchema schema) {
    //noinspection HardCodedStringLiteral
    Group rootGroup = new Group("root", null, null);
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(rootGroup);
    root.removeAllChildren();
    schema.fillActionGroups(root);
    final JTree defaultTree = new Tree(new DefaultTreeModel(root));

    final ArrayList<ActionUrl> actions = new ArrayList<ActionUrl>();

    TreeUtil.traverseDepth((TreeNode)tree.getModel().getRoot(), new TreeUtil.Traverse() {
      public boolean accept(Object node) {
        DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)node;
        if (treeNode.isLeaf()) {
          return true;
        }
        final ActionUrl url = getActionUrl(new TreePath(treeNode.getPath()), 0);
        url.getGroupPath().add(((Group)treeNode.getUserObject()).getName());
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
            url.getGroupPath().add(((Group)treeNode.getUserObject()).getName());
            actions.addAll(schema.getChildActions(url));
          }
        }
        return true;
      }
    });
    schema.setActions(actions);
  }

  private static void computeDiff(final ActionUrl[] defaultUserObjects,
                                  final ActionUrl[] currentUserObjects,
                                  final ArrayList<ActionUrl> actions) {
    Diff.Change change = Diff.buildChanges(defaultUserObjects, currentUserObjects);
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
    List<String>  path = new ArrayList<String>();
    for (int i = 0; i < treePath.getPath().length; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group) {
        path.add(((Group)o).getName());
      }
    }
    return getTreePath(0, path, tree.getModel().getRoot(), tree);
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
    DefaultMutableTreeNode node = component;
    final TreeNode parent = node.getParent();
    url.setAbsolutePosition(parent != null ? parent.getIndex(node) : 0);
    url.setActionType(actionType);
    return url;
  }


  public static TreePath getTreePath(JTree tree, ActionUrl url) {
    return getTreePath(0, url.getGroupPath(), tree.getModel().getRoot(), tree);
  }

  private static TreePath getTreePath(final int positionInPath, final List<String> path, final Object root, JTree tree) {
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
      currentPath = getTreePath(positionInPath + 1, path, child, tree);
      if (currentPath != null) {
        break;
      }
    }

    return currentPath;
  }


  private static ActionUrl[] getChildUserObjects(DefaultMutableTreeNode node, ActionUrl parent) {
    ArrayList<ActionUrl> result = new ArrayList<ActionUrl>();
    ArrayList<String> groupPath = new ArrayList<String>();
    groupPath.addAll(parent.getGroupPath());
    for (int i = 0; i < node.getChildCount(); i++) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(i);
      ActionUrl url = new ActionUrl();
      url.setGroupPath(groupPath);
      final Object userObject = child.getUserObject();
      url.setComponent(userObject instanceof Pair ? ((Pair)userObject).first : userObject);
      result.add(url);
    }
    return result.toArray(new ActionUrl[result.size()]);
  }

  public static MouseListener installPopupHandler(JComponent component, @NotNull final String groupId, final String place) {
    if (ApplicationManager.getApplication() == null) return new MouseAdapter(){};
    PopupHandler popupHandler = new PopupHandler() {
      public void invokePopup(Component comp, int x, int y) {
        ActionGroup group = (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(groupId);
        final ActionPopupMenu popupMenu = ActionManager.getInstance().createActionPopupMenu(place, group);
        popupMenu.getComponent().show(comp, x, y);
      }
    };
    component.addMouseListener(popupHandler);
    return popupHandler;
  }
}
