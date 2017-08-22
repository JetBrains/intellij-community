/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.PopupHandler;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.diff.Diff;
import com.intellij.util.diff.FilesTooBigForDiffException;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;

public class CustomizationUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ui.customization.CustomizationUtil");

  private CustomizationUtil() {
  }

  public static ActionGroup correctActionGroup(final ActionGroup group,
                                               final CustomActionsSchema schema,
                                               final String defaultGroupName,
                                               final String rootGroupName) {
    if (!schema.isCorrectActionGroup(group, defaultGroupName)){
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

    return new CustomisedActionGroup(text, group.isPopup(), group, schema, defaultGroupName, rootGroupName);
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
        final AnAction correctedAction = correctActionGroup(groupToCorrect, schema, "", rootGroupName);
        reorderedChildren.set(i, correctedAction);
      }
    }

    return reorderedChildren.toArray(new AnAction[reorderedChildren.size()]);
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
                                  @NotNull List<ActionUrl> actions) {
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
    ArrayList<String> groupPath = new ArrayList<>();
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

  @NotNull
  public static MouseListener installPopupHandler(JComponent component, @NotNull String groupId, String place) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup group = (ActionGroup)actionManager.getAction(groupId);
    return PopupHandler.installPopupHandler(component, group, place, actionManager);
  }
}
