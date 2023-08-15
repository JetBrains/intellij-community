// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.*;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.util.ArrayList;

public final class ActionUrl implements JDOMExternalizable {
  public static final int ADDED = 1;
  public static final int DELETED = -1;

  //temp action only
  public static final int MOVE = 2;

  private ArrayList<String> myGroupPath;
  private Object myComponent;
  private int myActionType;
  private int myAbsolutePosition;

  public int myInitialPosition = -1;

  private static final @NonNls String IS_GROUP = "is_group";
  private static final @NonNls String SEPARATOR = "seperator";
  private static final @NonNls String IS_ACTION = "is_action";
  private static final @NonNls String VALUE = "value";
  private static final @NonNls String PATH = "path";
  private static final @NonNls String ACTION_TYPE = "action_type";
  private static final @NonNls String POSITION = "position";
  private static final @NonNls String FORCE_POPUP = "forse_popup";


  public ActionUrl() {
    myGroupPath = new ArrayList<>();
  }

  public ActionUrl(final ArrayList<String> groupPath,
                   final Object component,
                   @MagicConstant(intValues = {ADDED, DELETED, MOVE}) int actionType,
                   final int position) {
    myGroupPath = groupPath;
    myComponent = component;
    myActionType = actionType;
    myAbsolutePosition = position;
  }

  public ArrayList<String> getGroupPath() {
    return myGroupPath;
  }

  public String getParentGroup() {
    return myGroupPath.get(myGroupPath.size() - 1);
  }

  public String getRootGroup() {
    return myGroupPath.size() >= 1 ? myGroupPath.get(1) : "";
  }

  public Object getComponent() {
    return myComponent;
  }

  public @Nullable AnAction getComponentAction() {
    if (myComponent instanceof Separator) {
      return Separator.getInstance();
    }
    if (myComponent instanceof String) {
      return ActionManager.getInstance().getAction((String)myComponent);
    }
    if (myComponent instanceof Group) {
      final String id = ((Group)myComponent).getId();
      if (id == null || id.length() == 0) {
        return ((Group)myComponent).constructActionGroup(true);
      }
      return ActionManager.getInstance().getAction(id);
    }
    return null;
  }

  @MagicConstant(intValues = {ADDED, DELETED, MOVE})
  public int getActionType() {
    return myActionType;
  }

  public void setActionType(@MagicConstant(intValues = {ADDED, DELETED, MOVE}) int actionType) {
    myActionType = actionType;
  }

  public int getAbsolutePosition() {
    return myAbsolutePosition;
  }

  public void setAbsolutePosition(final int absolutePosition) {
    myAbsolutePosition = absolutePosition;
  }

  public int getInitialPosition() {
    return myInitialPosition;
  }

  public void setInitialPosition(final int initialPosition) {
    myInitialPosition = initialPosition;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myGroupPath = new ArrayList<>();
    for (Element o : element.getChildren(PATH)) {
      myGroupPath.add(o.getAttributeValue(VALUE));
    }
    final @NlsSafe String attributeValue = element.getAttributeValue(VALUE);
    if (element.getAttributeValue(IS_ACTION) != null) {
      myComponent = attributeValue;
    }
    else if (element.getAttributeValue(SEPARATOR) != null) {
      myComponent = Separator.getInstance();
    }
    else if (element.getAttributeValue(IS_GROUP) != null) {
      final AnAction action = ActionManager.getInstance().getAction(attributeValue);
      Group group = action instanceof ActionGroup
                    ? ActionsTreeUtil.createGroup((ActionGroup)action, true, null)
                    : new Group(attributeValue, attributeValue, null);
      group.setForceShowAsPopup(Boolean.parseBoolean(element.getAttributeValue(FORCE_POPUP)));
      myComponent = group;
    }
    String actionTypeString = element.getAttributeValue(ACTION_TYPE);
    myActionType = actionTypeString == null ? -1 : Integer.parseInt(actionTypeString);
    myAbsolutePosition = Integer.parseInt(element.getAttributeValue(POSITION));
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    for (String s : myGroupPath) {
      Element path = new Element(PATH);
      path.setAttribute(VALUE, s);
      element.addContent(path);
    }
    if (myComponent instanceof String) {
      element.setAttribute(VALUE, (String)myComponent);
      element.setAttribute(IS_ACTION, Boolean.TRUE.toString());
    }
    else if (myComponent instanceof Separator) {
      element.setAttribute(SEPARATOR, Boolean.TRUE.toString());
    }
    else if (myComponent instanceof Group group) {
      final String groupId = group.getId() != null && !group.getId().isEmpty()
                             ? group.getId() : group.getName();
      element.setAttribute(VALUE, groupId != null ? groupId : "");
      element.setAttribute(IS_GROUP, Boolean.TRUE.toString());
      element.setAttribute(FORCE_POPUP, Boolean.toString(group.isForceShowAsPopup()));
    }
    element.setAttribute(ACTION_TYPE, Integer.toString(myActionType));
    element.setAttribute(POSITION, Integer.toString(myAbsolutePosition));
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public static void changePathInActionsTree(JTree tree, ActionUrl url) {
    if (url.myActionType == ADDED) {
      addPathToActionsTree(tree, url);
    }
    else if (url.myActionType == DELETED) {
      removePathFromActionsTree(tree, url);
    }
    else if (url.myActionType == MOVE) {
      movePathInActionsTree(tree, url);
    }
  }

  public static @Nullable DefaultMutableTreeNode addPathToActionsTree(JTree tree, ActionUrl url) {
    final TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath == null) return null;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
    final int absolutePosition = url.getAbsolutePosition();
    if (node.getChildCount() >= absolutePosition && absolutePosition >= 0) {
      DefaultMutableTreeNode newNode;
      if (url.getComponent() instanceof Group) {
        newNode = ActionsTreeUtil.createNode((Group)url.getComponent());
      }
      else {
        newNode = new DefaultMutableTreeNode(url.getComponent());
      }
      node.insert(newNode, absolutePosition);
      return newNode;
    }
    return null;
  }

  private static void removePathFromActionsTree(JTree tree, ActionUrl url) {
    if (url.myComponent == null) return;
    final TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath == null) return;
    DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
    final int absolutePosition = url.getAbsolutePosition();
    if (node.getChildCount() > absolutePosition && absolutePosition >= 0) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)node.getChildAt(absolutePosition);
      Object userObj = child.getUserObject();
      if (url.getComponent().equals(userObj instanceof Pair<?, ?> pair ? pair.first : userObj)) {
        node.remove(child);
      }
    }
  }

  private static void movePathInActionsTree(JTree tree, ActionUrl url) {
    final TreePath treePath = CustomizationUtil.getTreePath(tree, url);
    if (treePath != null) {
      if (treePath.getLastPathComponent() != null) {
        final DefaultMutableTreeNode parent = ((DefaultMutableTreeNode)treePath.getLastPathComponent());
        final int absolutePosition = url.getAbsolutePosition();
        final int initialPosition = url.getInitialPosition();
        if (parent.getChildCount() > absolutePosition && absolutePosition >= 0) {
          if (parent.getChildCount() > initialPosition && initialPosition >= 0) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode)parent.getChildAt(initialPosition);
            Object userObj = child.getUserObject();
            if (url.getComponent().equals(userObj instanceof Pair<?, ?> pair ? pair.first : userObj)) {
              parent.remove(child);
              parent.insert(child, absolutePosition);
            }
          }
        }
      }
    }
  }

  public static ArrayList<String> getGroupPath(final TreePath treePath, boolean includeSelf) {
    final ArrayList<String> result = new ArrayList<>();
    int length = treePath.getPath().length - (includeSelf ? 0 : 1);
    for (int i = 0; i < length; i++) {
      Object o = ((DefaultMutableTreeNode)treePath.getPath()[i]).getUserObject();
      if (o instanceof Group) {
        result.add(((Group)o).getName());
      }
    }
    return result;
  }

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof ActionUrl url)) {
      return false;
    }
    Object comp = myComponent instanceof Pair ? ((Pair<?, ?>)myComponent).first : myComponent;
    Object thatComp = url.myComponent instanceof Pair ? ((Pair<?, ?>)url.myComponent).first : url.myComponent;
    return Comparing.equal(comp, thatComp)
           && myGroupPath.equals(url.myGroupPath)
           && myAbsolutePosition == url.myAbsolutePosition
           && myActionType == url.myActionType;
  }

  @Override
  public int hashCode() {
    int result = myComponent != null ? myComponent.hashCode() : 0;
    result += 29 * myGroupPath.hashCode();
    return result;
  }

  public void setComponent(final Object object) {
    myComponent = object;
  }

  public void setGroupPath(final ArrayList<String> groupPath) {
    myGroupPath = groupPath;
  }

  @Override
  public @NonNls String toString() {
    return "ActionUrl{" +
           "myGroupPath=" + myGroupPath +
           ", myComponent=" + myComponent +
           ", myActionType=" + myActionType +
           ", myAbsolutePosition=" + myAbsolutePosition +
           ", myInitialPosition=" + myInitialPosition +
           '}';
  }

  public ActionUrl copy() {
    ActionUrl url = new ActionUrl(new ArrayList<>(getGroupPath()), getComponent(), getActionType(), getAbsolutePosition());
    url.setInitialPosition(getInitialPosition());
    return url;
  }

  public ActionUrl getInverted() {
    ActionUrl copy = copy();
    if (myActionType == ADDED || myActionType == DELETED) {
      copy.setActionType(-myActionType);
    }
    else {
      copy.setInitialPosition(myAbsolutePosition);
      copy.setAbsolutePosition(myInitialPosition);
    }
    return copy;
  }
}
