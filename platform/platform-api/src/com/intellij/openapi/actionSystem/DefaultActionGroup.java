// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Supplier;

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
  private static final Logger LOG = Logger.getInstance(DefaultActionGroup.class);
  private static final String CANT_ADD_ITSELF = "Cannot add a group to itself: ";
  private static final String CANT_ADD_ACTION_TWICE = "Cannot add an action twice: ";
  /**
   * Contains instances of AnAction
   */
  private final List<AnAction> mySortedChildren = ContainerUtil.createLockFreeCopyOnWriteList();
  /**
   * Contains instances of Pair
   */
  private final List<Pair<AnAction, Constraints>> myPairs = ContainerUtil.createLockFreeCopyOnWriteList();
  private int myModificationStamp;

  public DefaultActionGroup() {
    this(Presentation.NULL_STRING, false);
  }

  /**
   * Creates an action group containing the specified actions.
   *
   * @param actions the actions to add to the group
   */
  public DefaultActionGroup(AnAction @NotNull ... actions) {
    this(Arrays.asList(actions));
  }

  /**
   * Creates an action group containing the specified actions.
   *
   * @param actions the actions to add to the group
   */
  public DefaultActionGroup(@NotNull List<? extends AnAction> actions) {
    this(Presentation.NULL_STRING, actions);
  }

  public DefaultActionGroup(@NotNull Supplier<@ActionText String> name, @NotNull List<? extends AnAction> actions) {
    this(name, false);
    addActions(actions);
  }

  public DefaultActionGroup(@Nullable @ActionText String name, @NotNull List<? extends AnAction> actions) {
    this(() -> name, actions);
  }

  public DefaultActionGroup(@Nullable @ActionText String shortName, boolean popup) {
    this(() -> shortName, popup);
  }

  protected DefaultActionGroup(@NotNull Supplier<@ActionText String> shortName, boolean popup) {
    super(shortName, popup);
  }

  public static DefaultActionGroup createPopupGroup(@NotNull Supplier<@ActionText String> shortName) {
    return new DefaultActionGroup(shortName, true);
  }

  public static DefaultActionGroup createFlatGroup(@NotNull Supplier<@ActionText String> shortName) {
    return new DefaultActionGroup(shortName, false);
  }

  public static DefaultActionGroup createPopupGroupWithEmptyText() {
    return createPopupGroup(() -> "");
  }

  private void incrementModificationStamp() {
    myModificationStamp++;
  }

  public int getModificationStamp() {
    return myModificationStamp;
  }

  private void addActions(@NotNull List<? extends AnAction> actions) {
    Set<Object> actionSet = new HashSet<>();
    List<AnAction> uniqueActions = new ArrayList<>(actions.size());
    for (AnAction action : actions) {
      if (action == this) throw new IllegalArgumentException(CANT_ADD_ITSELF + action);
      if (!(action instanceof Separator) && !actionSet.add(action)) {
        LOG.error(CANT_ADD_ACTION_TWICE + action);
        continue;
      }
      uniqueActions.add(action);
    }
    mySortedChildren.addAll(uniqueActions);
    incrementModificationStamp();
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

  @NotNull
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

  @NotNull
  public final ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint) {
    return addAction(action, constraint, ActionManager.getInstance());
  }

  public final void add(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    addAction(action, constraint, actionManager);
  }

  @NotNull
  public final ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    if (action == this) {
      throw new IllegalArgumentException(CANT_ADD_ITSELF + action);
    }

    // check that action isn't already registered
    if (!(action instanceof Separator) && containsAction(action)) {
      LOG.error(CANT_ADD_ACTION_TWICE + action);
      remove(action, actionManager.getId(action));
    }

    constraint = (Constraints)constraint.clone();

    if (constraint.myAnchor == Anchor.FIRST) {
      mySortedChildren.add(0, action);
    }
    else if (constraint.myAnchor == Anchor.LAST) {
      mySortedChildren.add(action);
    }
    else {
      myPairs.add(Pair.create(action, constraint));
    }
    addAllToSortedList(actionManager);
    incrementModificationStamp();
    return new ActionInGroup(this, action);
  }

  private boolean containsAction(@NotNull AnAction action) {
    if (mySortedChildren.contains(action)) return true;
    for (Pair<AnAction, Constraints> pair : myPairs) {
      if (action.equals(pair.first)) return true;
    }
    return false;
  }

  private void addAllToSortedList(@NotNull ActionManager actionManager) {
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

  private boolean addToSortedList(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
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

  private static int findIndex(String actionId, @NotNull List<? extends AnAction> actions, @NotNull ActionManager actionManager) {
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
  public final void remove(@NotNull AnAction action) {
    remove(action, ActionManager.getInstance());
  }

  public final void remove(@NotNull AnAction action, @NotNull ActionManager actionManager) {
    remove(action, actionManager.getId(action));
  }

  public final void remove(@NotNull AnAction action, @Nullable String id) {
    if (!mySortedChildren.remove(action) &&
        !mySortedChildren.removeIf(oldAction ->
                                     oldAction instanceof ActionStubBase && ((ActionStubBase)oldAction).getId().equals(id))) {
      for (int i = 0; i < myPairs.size(); i++) {
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (pair.first.equals(action) || (pair.first instanceof ActionStubBase && ((ActionStubBase)pair.first).getId().equals(id))) {
          myPairs.remove(i);
          incrementModificationStamp();
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
    incrementModificationStamp();
  }

  /**
   * Replaces specified action with the a one.
   */
  public boolean replaceAction(@NotNull AnAction oldAction, @NotNull AnAction newAction) {
    int index = mySortedChildren.indexOf(oldAction);
    if (index >= 0) {
      mySortedChildren.set(index, newAction);
      incrementModificationStamp();
      return true;
    }
    else {
      for (int i = 0; i < myPairs.size(); i++) {
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (pair.first.equals(newAction)) {
          myPairs.set(i, Pair.create(newAction, pair.second));
          incrementModificationStamp();
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
    incrementModificationStamp();
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    return getChildren(e, e != null ? e.getActionManager() : ActionManager.getInstance());
  }

  /**
   * Returns group's children in the order determined by constraints.
   *
   * @param e not used
   * @return An array of children actions
   */
  @Override
  public final AnAction @NotNull [] getChildren(@Nullable AnActionEvent e, @NotNull ActionManager actionManager) {
    boolean hasNulls = false;
    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPairs.size()];
    for (int i = 0; i < sortedSize; i++) {
      AnAction action = mySortedChildren.get(i);
      if (action == null) {
        LOG.error("Empty sorted child: " + this + ", " + getClass() + "; index=" + i);
      }
      if (action instanceof ActionStubBase) {
        action = unStub(actionManager, (ActionStubBase)action);
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
      else if (action instanceof ActionStubBase) {
        action = unStub(actionManager, (ActionStubBase)action);
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
  private AnAction unStub(@NotNull ActionManager actionManager, @NotNull ActionStubBase stub) {
    try {
      AnAction action = actionManager.getAction(stub.getId());
      if (action == null) {
        LOG.error("Null child action in group " + this + " of class " + getClass() + ", id=" + stub.getId());
        return null;
      }
      replace((AnAction)stub, action);
      return action;
    }
    catch (ProcessCanceledException ex) {
      throw ex;
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

  public final AnAction @NotNull [] getChildActionsOrStubs() {
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

  public final void addAll(@NotNull ActionGroup group) {
    addAll(group.getChildren(null));
  }

  public final void addAll(@NotNull Collection<? extends AnAction> actionList) {
    addAll(actionList, ActionManager.getInstance());
  }

  public final void addAll(@NotNull Collection<? extends AnAction> actionList, @NotNull ActionManager actionManager) {
    if (actionList.isEmpty()) {
      return;
    }

    for (AnAction action : actionList) {
      addAction(action, Constraints.LAST, actionManager);
    }
  }

  public final void addAll(@NotNull AnAction @NotNull ... actions) {
    if (actions.length == 0) {
      return;
    }

    ActionManager actionManager = ActionManager.getInstance();
    for (AnAction action : actions) {
      addAction(action, Constraints.LAST, actionManager);
    }
  }

  public void addSeparator(@Nullable @NlsContexts.Separator String separatorText) {
    add(Separator.create(separatorText));
  }

  /**
   * Creates an action group with specified template text. It is necessary to redefine template text if group contains
   * user specific data such as Project name, file name, etc
   * @param templateText template text which will be used in statistics
   * @return action group
   */
  public static DefaultActionGroup createUserDataAwareGroup(String templateText) {
    return new DefaultActionGroup() {
      @Nullable
      @Override
      public String getTemplateText() {
        return templateText;
      }
    };
  }
}
