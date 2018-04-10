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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * A default implementation of {@link ActionGroup}. Provides the ability
 * to add children actions and separators between them. In most of the
 * cases you will be using this implementation but note that there are
 * cases (for example "Recent files" dialog) where children are determined
 * on rules different than just positional constraints, that's when you need
 * to implement your own {@code ActionGroup}.
 *
 * @see Constraints
 *
 * @see com.intellij.openapi.actionSystem.ComputableActionGroup
 *
 * @see com.intellij.ide.actions.NonEmptyActionGroup
 * @see com.intellij.ide.actions.NonTrivialActionGroup
 * @see com.intellij.ide.actions.SmartPopupActionGroup
 *
 */
public class DefaultActionGroup extends ActionGroup {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.actionSystem.DefaultActionGroup");
  private static final String CANT_ADD_ITSELF = "Cannot add a group to itself";
  /**
   * Contains instances of AnAction
   */
  private final List<AnAction> mySortedChildren = ContainerUtil.createLockFreeCopyOnWriteList();
  /**
   * Contains instances of Pair
   */
  private final List<Pair<AnAction, Constraints>> myPairs = ContainerUtil.createLockFreeCopyOnWriteList();

  public DefaultActionGroup() {
    this(null, false);
  }

  /**
   * Creates an action group containing the specified actions.
   *
   * @param actions the actions to add to the group
   * @since 9.0
   */
  public DefaultActionGroup(@NotNull AnAction... actions) {
    this(Arrays.asList(actions));
  }

  /**
   * Creates an action group containing the specified actions.
   *
   * @param actions the actions to add to the group
   * @since 13.0
   */
  public DefaultActionGroup(@NotNull List<? extends AnAction> actions) {
    this(null, actions);
  }

  public DefaultActionGroup(@Nullable String name, @NotNull List<? extends AnAction> actions) {
    this(name, actions, true);
  }

  public DefaultActionGroup(@Nullable String name, @NotNull List<? extends AnAction> actions, boolean validate) {
    this(name, false);
    addActions(actions, validate);
  }

  public DefaultActionGroup(@Nullable String shortName, boolean popup) {
    super(shortName, popup);
  }

  private void addActions(@NotNull List<? extends AnAction> actions, boolean validate) {
    if (validate) {
      HashSet<Object> actionSet = new HashSet<>();
      for (AnAction action : actions) {
        if (action == this) throw new IllegalArgumentException(CANT_ADD_ITSELF);
        if (!(action instanceof Separator) && !actionSet.add(action)) throw new ActionDuplicationException(action);
      }
    }
    mySortedChildren.addAll(actions);
  }

  /**
   * Adds the specified action to the tail.
   *
   * @param action        Action to be added
   * @param actionManager ActionManager instance
   */
  public final void add(@NotNull AnAction action, @NotNull ActionManager actionManager) {
    add(action, Constraints.LAST, actionManager);
  }

  public final void add(@NotNull AnAction action) {
    addAction(action, Constraints.LAST);
  }

  public final ActionInGroup addAction(@NotNull AnAction action) {
    return addAction(action, Constraints.LAST);
  }

  /**
   * Adds a separator to the tail.
   */
  public final void addSeparator() {
    add(Separator.create());
  }

  /**
   * Adds the specified action with the specified constraint.
   *
   * @param action     Action to be added; cannot be null
   * @param constraint Constraint to be used for determining action's position; cannot be null
   * @throws IllegalArgumentException in case when:
   *                                  <li>action is null
   *                                  <li>constraint is null
   *                                  <li>action is already in the group
   */
  public final void add(@NotNull AnAction action, @NotNull Constraints constraint) {
    add(action, constraint, ActionManager.getInstance());
  }

  public final ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint) {
    return addAction(action, constraint, ActionManager.getInstance());
  }

  public final void add(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    addAction(action, constraint, actionManager);
  }

  public final ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    if (action == this) throw new IllegalArgumentException(CANT_ADD_ITSELF);
    // Check that action isn't already registered
    if (!(action instanceof Separator)) {
      if (mySortedChildren.contains(action)) throw new ActionDuplicationException(action);
      for (Pair<AnAction, Constraints> pair : myPairs) {
        if (action.equals(pair.first)) throw new ActionDuplicationException(action);
      }
    }

    constraint = (Constraints)constraint.clone();

    if (constraint.myAnchor == Anchor.FIRST) {
      mySortedChildren.add(0, action);
    }
    else if (constraint.myAnchor == Anchor.LAST) {
      mySortedChildren.add(action);
    }
    else {
      if (addToSortedList(action, constraint, actionManager)) {
        actionAdded(action, actionManager);
      }
      else {
        myPairs.add(Pair.create(action, constraint));
      }
    }

    return new ActionInGroup(this, action);
  }

  private void actionAdded(AnAction addedAction, ActionManager actionManager) {
    String addedActionId = addedAction instanceof ActionStub ? ((ActionStub)addedAction).getId() : actionManager.getId(addedAction);
    if (addedActionId == null) {
      return;
    }
    outer:
    while (!myPairs.isEmpty()) {
      for (int i = 0; i < myPairs.size(); i++) {
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (addToSortedList(pair.first, pair.second, actionManager)) {
          myPairs.remove(i);
          continue outer;
        }
      }
      break;
    }
  }

  private boolean addToSortedList(@NotNull AnAction action, Constraints constraint, ActionManager actionManager) {
    int index = findIndex(constraint.myRelativeToActionId, mySortedChildren, actionManager);
    if (index == -1) {
      return false;
    }
    if (constraint.myAnchor == Anchor.BEFORE) {
      mySortedChildren.add(index, action);
    }
    else {
      mySortedChildren.add(index + 1, action);
    }
    return true;
  }

  private static int findIndex(String actionId, List<? extends AnAction> actions, ActionManager actionManager) {
    for (int i = 0; i < actions.size(); i++) {
      AnAction action = actions.get(i);
      if (action instanceof ActionStub) {
        if (((ActionStub)action).getId().equals(actionId)) {
          return i;
        }
      }
      else {
        String id = actionManager.getId(action);
        if (id != null && id.equals(actionId)) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Removes specified action from group.
   *
   * @param action Action to be removed
   */
  public final void remove(AnAction action) {
    if (!mySortedChildren.remove(action)) {
      for (int i = 0; i < myPairs.size(); i++) {
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (pair.first.equals(action)) {
          myPairs.remove(i);
          break;
        }
      }
    }
  }

  /**
   * Removes all children actions (separators as well) from the group.
   */
  public final void removeAll() {
    mySortedChildren.clear();
    myPairs.clear();
  }


  /**
   * Replaces specified action with the a one.
   */
  public boolean replaceAction(@NotNull AnAction oldAction, @NotNull AnAction newAction) {
    int index = mySortedChildren.indexOf(oldAction);
    if (index >= 0) {
      mySortedChildren.set(index, newAction);
      return true;
    }
    else {
      for (int i = 0; i < myPairs.size(); i++) {
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (pair.first.equals(newAction)) {
          myPairs.set(i, Pair.create(newAction, pair.second));
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Copies content from {@code group}.
   * @param other group to copy from
   */
  public void copyFromGroup(@NotNull DefaultActionGroup other) {
    copyFrom(other);
    setPopup(other.isPopup());

    mySortedChildren.clear();
    mySortedChildren.addAll(other.mySortedChildren);

    myPairs.clear();
    myPairs.addAll(other.myPairs);
  }

  /**
   * Returns group's children in the order determined by constraints.
   *
   * @param e not used
   * @return An array of children actions
   */
  @Override
  @NotNull
  public final AnAction[] getChildren(@Nullable AnActionEvent e) {
    boolean hasNulls = false;

    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPairs.size()];
    for (int i = 0; i < sortedSize; i++) {
      AnAction action = mySortedChildren.get(i);
      if (action == null) {
        LOG.error("Empty sorted child: " + this + ", " + getClass() + "; index=" + i);
      }
      if (action instanceof ActionStub) {
        action = unStub(e, (ActionStub)action);
        if (action == null) {
          LOG.error("Can't unstub " + mySortedChildren.get(i));
        }
        else {
          mySortedChildren.set(i, action);
        }
      }

      hasNulls |= action == null;
      children[i] = action;
    }
    for (int i = 0; i < myPairs.size(); i++) {
      final Pair<AnAction, Constraints> pair = myPairs.get(i);
      AnAction action = pair.first;
      if (action == null) {
        LOG.error("Empty pair child: " + this + ", " + getClass() + "; index=" + i);
      }
      else if (action instanceof ActionStub) {
        action = unStub(e, (ActionStub)action);
        if (action == null) {
          LOG.error("Can't unstub " + pair);
        }
        else {
          myPairs.set(i, Pair.create(action, pair.second));
        }
      }

      hasNulls |= action == null;
      children[i + sortedSize] = action;
    }

    if (hasNulls) {
      return ContainerUtil.mapNotNull(children, FunctionUtil.id(), AnAction.EMPTY_ARRAY);
    }
    return children;
  }

  @Nullable
  private AnAction unStub(@Nullable AnActionEvent e, final ActionStub stub) {
    ActionManager actionManager = e != null ? e.getActionManager() : ActionManager.getInstance();
    try {
      AnAction action = actionManager.getAction(stub.getId());
      if (action == null) {
        LOG.error("Null child action in group " + this + " of class " + getClass() + ", id=" + stub.getId());
        return null;
      }
      replace(stub, action);
      return action;
    }
    catch (Throwable e1) {
      LOG.error(e1);
      return null;
    }
  }

  /**
   * Returns the number of contained children (including separators).
   *
   * @return number of children in the group
   */
  public final int getChildrenCount() {
    return mySortedChildren.size() + myPairs.size();
  }

  @NotNull
  public final AnAction[] getChildActionsOrStubs() {
    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPairs.size()];
    for (int i = 0; i < sortedSize; i++) {
      children[i] = mySortedChildren.get(i);
    }
    for (int i = 0; i < myPairs.size(); i++) {
      children[i + sortedSize] = myPairs.get(i).first;
    }
    return children;
  }

  public final void addAll(ActionGroup group) {
    for (AnAction each : group.getChildren(null)) {
      add(each);
    }
  }

  public final void addAll(Collection<? extends AnAction> actionList) {
    for (AnAction each : actionList) {
      add(each);
    }
  }

  public final void addAll(AnAction... actions) {
    for (AnAction each : actions) {
      add(each);
    }
  }

  public void addSeparator(@Nullable String separatorText) {
    add(Separator.create(separatorText));
  }

  private static class ActionDuplicationException extends IllegalArgumentException {
    public ActionDuplicationException(@NotNull AnAction action) {
      super("cannot add an action twice: " + action);
    }
  }
}
