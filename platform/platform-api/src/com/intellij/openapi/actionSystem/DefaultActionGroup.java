// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A default implementation of {@link ActionGroup}.
 * Provides the ability to add child actions with first/last/before/after constraints,
 * as well as separators between them.
 * <p>
 * In most of the cases, you will be using this implementation,
 * but note that there are cases like the "Recent files" dialog
 * where children are determined on rules different from just positional constraints,
 * that's when you need to implement your own {@code ActionGroup}.
 *
 * @see Constraints
 * @see com.intellij.ide.actions.NonEmptyActionGroup
 * @see com.intellij.ide.actions.NonTrivialActionGroup
 * @see com.intellij.ide.actions.SmartPopupActionGroup
 */
public class DefaultActionGroup extends ActionGroup {
  private static final Logger LOG = Logger.getInstance(DefaultActionGroup.class);

  private final List<AnAction> mySortedChildren = new ArrayList<>();
  private final List<AnAction> myPendingActions = new ArrayList<>();
  private final HashMap<AnAction, Constraints> myConstraints = new HashMap<>();
  private int myModificationStamp;

  public DefaultActionGroup() {
    super();
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

  public DefaultActionGroup(@NotNull Supplier<@ActionText String> shortName, boolean popup) {
    super(shortName, popup);
  }

  public DefaultActionGroup(@Nullable @ActionText String text,
                            @Nullable @NlsActions.ActionDescription String description,
                            @Nullable Icon icon) {
    super(text, description, icon);
  }

  public DefaultActionGroup(@NotNull Supplier<@ActionText String> dynamicText,
                            @NotNull Supplier<@NlsActions.ActionDescription String> dynamicDescription,
                            @Nullable Icon icon) {
    super(dynamicText, dynamicDescription, icon);
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

  private synchronized void addActions(@NotNull List<? extends AnAction> actions) {
    Set<AnAction> actionSet = new HashSet<>(actions.size());
    List<AnAction> uniqueActions = new ArrayList<>(actions.size());
    for (AnAction action : actions) {
      if (action == this) {
        LOG.error(newThisGroupToItselfAddedException());
      }
      else if (action == null) {
        LOG.error(nullActionAddedToTheGroupException());
      }
      else if (!(action instanceof Separator || actionSet.add(action))) {
        LOG.error(newDuplicateActionAddedException(action));
      }
      else {
        uniqueActions.add(action);
      }
    }
    mySortedChildren.addAll(uniqueActions);
    incrementModificationStamp();
  }

  private IllegalArgumentException newThisGroupToItselfAddedException() {
    return new IllegalArgumentException("Cannot add a group to itself: " + this + " (" + getTemplateText() + ")");
  }

  private IllegalArgumentException nullActionAddedToTheGroupException() {
    return new IllegalArgumentException("Cannot add null action to the group " + this + " (" + getTemplateText() + ")");
  }

  private static IllegalArgumentException newDuplicateActionAddedException(@NotNull AnAction action) {
    return new IllegalArgumentException(
      "Cannot add an action twice: " + action + " (" +
      (action instanceof ActionStub ? ((ActionStub)action).getClassName() : action.getClass().getName()) + ")");
  }

  /**
   * Adds the specified action to the tail.
   */
  public final void add(@NotNull AnAction action, @NotNull ActionManager actionManager) {
    add(action, Constraints.LAST, actionManager);
  }

  public final void add(@NotNull AnAction action) {
    addAction(action, Constraints.LAST);
  }

  public final @NotNull ActionInGroup addAction(@NotNull AnAction action) {
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
   * @param constraint determines the action's position
   * @throws IllegalArgumentException in case when:
   *                                  <li>action is null
   *                                  <li>constraint is null
   *                                  <li>action is already in the group
   */
  public final void add(@NotNull AnAction action, @NotNull Constraints constraint) {
    addAction(action, constraint, ActionManager.getInstance());
  }

  public final @NotNull ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint) {
    return addAction(action, constraint, ActionManager.getInstance());
  }

  public final void add(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    addAction(action, constraint, actionManager);
  }

  public final synchronized @NotNull ActionInGroup addAction(@NotNull AnAction action,
                                                             @NotNull Constraints constraint,
                                                             @NotNull ActionManager actionManager) {
    return addAction(action, constraint, actionManager::getId);
  }

  @ApiStatus.Internal
  public final synchronized @NotNull ActionInGroup addAction(@NotNull AnAction action,
                                                             @NotNull Constraints constraint,
                                                             @NotNull Function<@NotNull AnAction, @Nullable String> actionToId) {
    if (action == this) {
      throw newThisGroupToItselfAddedException();
    }

    if (!(action instanceof Separator) && containsAction(action)) {
      LOG.error(newDuplicateActionAddedException(action));
      remove(action, actionToId.apply(action));
    }

    constraint = (Constraints)constraint.clone();

    if (constraint.myAnchor == Anchor.FIRST) {
      mySortedChildren.add(0, action);
    }
    else if (constraint.myAnchor == Anchor.LAST) {
      mySortedChildren.add(action);
    }
    else {
      myPendingActions.add(action);
    }
    myConstraints.put(action, constraint);
    addAllToSortedList(actionToId);
    incrementModificationStamp();
    return new ActionInGroup(this, action);
  }

  public synchronized boolean containsAction(@NotNull AnAction action) {
    return mySortedChildren.contains(action) || myPendingActions.contains(action);
  }

  private void addAllToSortedList(@NotNull Function<@NotNull AnAction, @Nullable String> actionToId) {
    outer:
    while (!myPendingActions.isEmpty()) {
      for (int i = 0; i < myPendingActions.size(); i++) {
        AnAction pendingAction = myPendingActions.get(i);
        Constraints constraints = myConstraints.get(pendingAction);
        if (constraints != null && addToSortedList(pendingAction, constraints, actionToId)) {
          myPendingActions.remove(i);
          continue outer;
        }
      }
      break;
    }
  }

  private boolean addToSortedList(@NotNull AnAction action,
                                  @NotNull Constraints constraint,
                                  @NotNull Function<@NotNull AnAction, @Nullable String> actionToId) {
    String relativeToActionId = constraint.myRelativeToActionId;
    int index = relativeToActionId == null ? -1 : findIndex(relativeToActionId, mySortedChildren, actionToId);
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

  private static int findIndex(@NotNull String actionId,
                               @NotNull List<? extends AnAction> actions,
                               @NotNull Function<@NotNull AnAction, @Nullable String> actionToId) {
    for (int i = 0; i < actions.size(); i++) {
      AnAction action = actions.get(i);
      if (action instanceof ActionStub) {
        if (((ActionStub)action).getId().equals(actionId)) {
          return i;
        }
      }
      else {
        String id = actionToId.apply(action);
        if (id != null && id.equals(actionId)) {
          return i;
        }
      }
    }
    return -1;
  }

  /**
   * Removes the specified action from the group.
   */
  public final void remove(@NotNull AnAction action) {
    remove(action, ActionManager.getInstance());
  }

  public final void remove(@NotNull AnAction action, @NotNull ActionManager actionManager) {
    remove(action, actionManager.getId(action));
  }

  public final synchronized void remove(@NotNull AnAction action, @Nullable String id) {
    Predicate<AnAction> matchesAction = o -> o.equals(action) || (o instanceof ActionStubBase stub && stub.getId().equals(id));
    boolean removed = mySortedChildren.removeIf(matchesAction);
    removed = removed || myPendingActions.removeIf(matchesAction);
    myConstraints.keySet().removeIf(matchesAction);
    if (removed) {
      incrementModificationStamp();
    }
  }

  /**
   * Removes all child actions (separators as well) from the group.
   */
  public final synchronized void removeAll() {
    mySortedChildren.clear();
    myPendingActions.clear();
    myConstraints.clear();
    incrementModificationStamp();
  }

  /**
   * Replaces the specified action with another.
   */
  public synchronized boolean replaceAction(@NotNull AnAction oldAction, @NotNull AnAction newAction) {
    int index = mySortedChildren.indexOf(oldAction);
    if (index >= 0) {
      mySortedChildren.set(index, newAction);
      replaceConstraint(oldAction, newAction);
      incrementModificationStamp();
      return true;
    }
    else {
      int indexOld = myPendingActions.indexOf(oldAction);
      if (indexOld >= 0) {
        myPendingActions.set(indexOld, newAction);
        replaceConstraint(oldAction, newAction);
        incrementModificationStamp();
        return true;
      }
    }
    return false;
  }

  private void replaceConstraint(AnAction oldAction, AnAction newAction) {
    Constraints constraint = myConstraints.get(oldAction);
    if (constraint != null) {
      myConstraints.put(newAction, constraint);
      myConstraints.remove(oldAction);
    }
  }

  /**
   * Copies content from {@code other}.
   */
  public synchronized void copyFromGroup(@NotNull DefaultActionGroup other) {
    copyFrom(other);
    setPopup(other.isPopup());

    mySortedChildren.clear();
    mySortedChildren.addAll(other.mySortedChildren);

    myPendingActions.clear();
    myPendingActions.addAll(other.myPendingActions);

    myConstraints.clear();
    myConstraints.putAll(other.myConstraints);
    incrementModificationStamp();
  }

  @Override
  public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
    if (e == null) reportGetChildrenForNullEvent();
    return getChildren(e != null ? e.getActionManager() : ActionManager.getInstance());
  }

  private static void reportGetChildrenForNullEvent() {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;
    LOG.error("Do not call `getChildren(null)`. Do not expand action groups manually. " +
              "Reuse `AnActionEvent.updateSession` by composing, wrapping, and postprocessing action groups. " +
              "Otherwise, use `getChildActionsOrStubs()` or `getChildren(ActionManager)`");
  }

  /**
   * Returns the group's unstubbed actions in the order determined by the constraints.
   *
   * @see DefaultActionGroup#getChildActionsOrStubs()
   */
  public final AnAction @NotNull [] getChildren(@NotNull ActionManager actionManager) {
    int modCount;
    AnAction[] actionOrStubs;
    synchronized (this) {
      modCount = myModificationStamp;
      actionOrStubs = getChildActionsOrStubs();
    }
    while (true) {
      Map<ActionStubBase, AnAction> stubMap = null;
      boolean hasNulls = false;
      for (AnAction o : actionOrStubs) {
        hasNulls |= o == null;
        if (!(o instanceof ActionStubBase stub)) continue;
        try {
          AnAction action = actionManager.getAction(stub.getId());
          if (action != null) {
            if (stubMap == null) stubMap = new HashMap<>();
            stubMap.put(stub, action);
          }
        }
        catch (ProcessCanceledException ex) {
          throw ex;
        }
        catch (Throwable ex) {
          LOG.error(ex);
        }
      }
      if (stubMap == null && !hasNulls) {
        return actionOrStubs;
      }
      synchronized (this) {
        if (modCount != myModificationStamp) {
          modCount = myModificationStamp;
          actionOrStubs = getChildActionsOrStubs();
        }
        else {
          replaceStubsAndNulls(stubMap != null ? stubMap : Collections.emptyMap());
          return getChildActionsOrStubs();
        }
      }
    }
  }

  private synchronized void replaceStubsAndNulls(@NotNull Map<ActionStubBase, AnAction> stubMap) {
    for (ListIterator<AnAction> it = mySortedChildren.listIterator(); it.hasNext();) {
      AnAction action = it.next();
      if (action == null) {
        LOG.error("Empty sorted child: " + this + ", " + getClass() + "; index=" + it.previousIndex());
        it.remove();
      }
      else if (action instanceof ActionStubBase) {
        AnAction replacement = stubMap.get((ActionStubBase)action);
        if (replacement != null) {
          it.set(replacement);
          replaceConstraint(action, replacement);
          replace(action, replacement);
        }
        else {
          myConstraints.remove(action);
          it.remove();
        }
      }
    }
    for (ListIterator<AnAction> it = myPendingActions.listIterator(); it.hasNext(); ) {
      AnAction action = it.next();
      if (action == null) {
        LOG.error("Empty pair child: " + this + ", " + getClass() + "; index=" + it.previousIndex());
        it.remove();
      }
      else if (action instanceof ActionStubBase) {
        AnAction replacement = stubMap.get((ActionStubBase)action);
        if (replacement != null) {
          it.set(replacement);
          replaceConstraint(action, replacement);
          replace(action, replacement);
        }
        else {
          myConstraints.remove(action);
          it.remove();
        }
      }
    }
  }

  /**
   * Returns the number of contained children (including separators).
   */
  public final synchronized int getChildrenCount() {
    return mySortedChildren.size() + myPendingActions.size();
  }

  /**
   * @see DefaultActionGroup#getChildren(ActionManager)
   */
  public final synchronized AnAction @NotNull [] getChildActionsOrStubs() {
    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPendingActions.size()];
    for (int i = 0; i < sortedSize; i++) {
      children[i] = mySortedChildren.get(i);
    }
    for (int i = 0; i < myPendingActions.size(); i++) {
      children[i + sortedSize] = myPendingActions.get(i);
    }
    return children;
  }

  /** @deprecated Prefer other {@link #add} and {@link #addAll} variants */
  @Deprecated
  public final void addAll(@NotNull ActionGroup group) {
    addAll(group instanceof DefaultActionGroup o ? o.getChildActionsOrStubs() :
           group.getChildren(null));
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

  public synchronized @Nullable Constraints getConstraints(@NotNull AnAction action) {
    return myConstraints.get(action);
  }
}
