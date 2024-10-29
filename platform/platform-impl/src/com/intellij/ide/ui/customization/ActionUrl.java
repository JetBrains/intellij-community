// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil;
import com.intellij.openapi.keymap.impl.ui.Group;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.intellij.lang.annotations.MagicConstant;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Objects;

@ApiStatus.Internal
public final class ActionUrl implements JDOMExternalizable {
  public static final int ADDED = 1;
  public static final int DELETED = -1;
  public static final int MOVE = 2;

  private static final @NonNls String IS_GROUP = "is_group";
  private static final @NonNls String SEPARATOR = "seperator";
  private static final @NonNls String IS_ACTION = "is_action";
  private static final @NonNls String VALUE = "value";
  private static final @NonNls String PATH = "path";
  private static final @NonNls String ACTION_TYPE = "action_type";
  private static final @NonNls String POSITION = "position";
  private static final @NonNls String FORCE_POPUP = "forse_popup";

  private static final int TYPE_NONE = 0;
  private static final int TYPE_SEPARATOR = 1;
  private static final int TYPE_ACTION = 2;
  private static final int TYPE_GROUP = 3;
  private static final int TYPE_POPUP_GROUP = 4;

  private @NotNull ArrayList<String> myGroupPath;
  private @Nullable String myComponentId;
  @MagicConstant(intValues = {TYPE_NONE, TYPE_SEPARATOR, TYPE_ACTION, TYPE_GROUP, TYPE_POPUP_GROUP})
  private int myComponentType;
  private @Nullable Object myComponent;
  private int myActionType;
  private int myAbsolutePosition;
  private int myInitialPosition = -1;


  public ActionUrl() {
    myGroupPath = new ArrayList<>();
  }

  public ActionUrl(@NotNull ArrayList<String> groupPath,
                   @Nullable Object component,
                   @MagicConstant(intValues = {ADDED, DELETED, MOVE}) int actionType,
                   int position) {
    myGroupPath = groupPath;
    setComponent(component);
    myActionType = actionType;
    myAbsolutePosition = position;
  }

  private ActionUrl(@NotNull ArrayList<String> groupPath,
                    @Nullable String componentId,
                    int componentType,
                    @Nullable Object component,
                    int actionType,
                    int absolutePosition,
                    int initialPosition) {
    myGroupPath = groupPath;
    myComponentId = componentId;
    myComponentType = componentType;
    myComponent = component;
    myActionType = actionType;
    myAbsolutePosition = absolutePosition;
    myInitialPosition = initialPosition;
  }

  public @NotNull ArrayList<String> getGroupPath() {
    return myGroupPath;
  }

  public void setGroupPath(@NotNull ArrayList<String> groupPath) {
    myGroupPath = groupPath;
  }

  public String getParentGroup() {
    return myGroupPath.get(myGroupPath.size() - 1);
  }

  public String getRootGroup() {
    return !myGroupPath.isEmpty() ? myGroupPath.get(1) : "";
  }

  public @Nullable String getComponentId() {
    return myComponentId;
  }

  // action or string, separator, group
  public @Nullable Object getComponent() {
    if (myComponent != null || myComponentType == TYPE_NONE) return myComponent;
    switch (myComponentType) {
      case TYPE_SEPARATOR -> myComponent = Separator.getInstance();
      case TYPE_ACTION -> myComponent = myComponentId;
      case TYPE_GROUP, TYPE_POPUP_GROUP -> {
        Objects.requireNonNull(myComponentId);
        AnAction action = ActionManager.getInstance().getActionOrStub(myComponentId);
        //noinspection HardCodedStringLiteral
        Group group = action instanceof ActionGroup o
                      ? ActionsTreeUtil.createGroup(o, true, null)
                      : new Group(myComponentId, myComponentId);
        group.setForceShowAsPopup(myComponentType == TYPE_POPUP_GROUP);
        myComponent = group;
      }
    }
    return myComponent;
  }

  public void setComponent(@Nullable Object c) {
    myComponent = c;
    myComponentType =
      c instanceof Separator ? TYPE_SEPARATOR :
      c instanceof String || c instanceof AnAction ? TYPE_ACTION :
      c instanceof Group o ? (o.isForceShowAsPopup() ? TYPE_POPUP_GROUP : TYPE_GROUP) :
      TYPE_NONE;
    myComponentId =
      c instanceof String o ? o :
      c instanceof Group o ? (StringUtil.isEmpty(o.getId()) ? o.getName() : o.getId()) :
      null;
  }

  public @Nullable AnAction getComponentAction() {
    Object component = getComponent();
    if (component instanceof Separator o) {
      return o;
    }
    if (component instanceof String o) {
      return ActionManager.getInstance().getAction(o);
    }
    if (component instanceof Group o) {
      String id = o.getId();
      if (StringUtil.isEmpty(id)) {
        return o.constructActionGroup(true);
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
    myComponentId = element.getAttributeValue(VALUE);
    myComponentType = element.getAttributeValue(IS_ACTION) != null ? TYPE_ACTION :
                      element.getAttributeValue(SEPARATOR) != null ? TYPE_SEPARATOR :
                      element.getAttributeValue(IS_GROUP) != null ? TYPE_GROUP : TYPE_NONE;
    if (myComponentType == TYPE_GROUP && Boolean.parseBoolean(element.getAttributeValue(FORCE_POPUP))) {
      myComponentType = TYPE_POPUP_GROUP;
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
    switch (myComponentType) {
      case TYPE_SEPARATOR ->
        element.setAttribute(SEPARATOR, Boolean.TRUE.toString());
      case TYPE_ACTION -> {
        element.setAttribute(VALUE, StringUtil.notNullize(myComponentId));
        element.setAttribute(IS_ACTION, Boolean.TRUE.toString());
      }
      case TYPE_GROUP, TYPE_POPUP_GROUP -> {
        element.setAttribute(VALUE, StringUtil.notNullize(myComponentId));
        element.setAttribute(IS_GROUP, Boolean.TRUE.toString());
        element.setAttribute(FORCE_POPUP, Boolean.toString(myComponentType == TYPE_POPUP_GROUP));
      }
    }
    element.setAttribute(ACTION_TYPE, Integer.toString(myActionType));
    element.setAttribute(POSITION, Integer.toString(myAbsolutePosition));
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public @NotNull ActionUrl copy() {
    return new ActionUrl(new ArrayList<>(myGroupPath), myComponentId, myComponentType, myComponent,
                         myActionType, myAbsolutePosition, myInitialPosition);
  }

  public @NotNull ActionUrl getInverted() {
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ActionUrl url)) return false;

    return myComponentType == url.myComponentType &&
           myActionType == url.myActionType &&
           myAbsolutePosition == url.myAbsolutePosition &&
           myInitialPosition == url.myInitialPosition &&
           myGroupPath.equals(url.myGroupPath) &&
           Objects.equals(myComponentId, url.myComponentId);
  }

  @Override
  public int hashCode() {
    int result = myGroupPath.hashCode();
    result = 31 * result + Objects.hashCode(myComponentId);
    result = 31 * result + myComponentType;
    result = 31 * result + myActionType;
    result = 31 * result + myAbsolutePosition;
    result = 31 * result + myInitialPosition;
    return result;
  }

  @Override
  public String toString() {
    return "ActionUrl{" + "myGroupPath=" + myGroupPath +
           ", myComponentId='" + myComponentId + '\'' +
           ", myComponentType=" + myComponentType +
           ", myComponent=" + myComponent +
           ", myActionType=" + myActionType +
           ", myAbsolutePosition=" + myAbsolutePosition +
           ", myInitialPosition=" + myInitialPosition +
           '}';
  }
}
