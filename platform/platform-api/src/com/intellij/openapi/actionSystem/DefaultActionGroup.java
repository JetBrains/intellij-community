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
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A default implementation of {@link ActionGroup}. Provides the ability
 * to add children actions and separators between them. In most of the
 * cases you will be using this implementation but note that there are
 * cases (for example "Recent files" dialog) where children are determined
 * on rules different than just positional constraints, that's when you need
 * to implement your own <code>ActionGroup</code>.
 *
 * @see Constraints
 */
public class DefaultActionGroup extends ActionGroup {
  /**
   * Contains instances of AnAction
   */
  private final List<AnAction> mySortedChildren = ContainerUtil.createEmptyCOWList();
  /**
   * Contains instances of Pair
   */
  private final List<Pair<AnAction,Constraints>> myPairs = ContainerUtil.createEmptyCOWList();

  public DefaultActionGroup(){
    this(null, false);
  }

  /**
   * Creates an action group containing the specified actions.
   *
   * @param actions the actions to add to the group
   * @since 9.0
   */
  public DefaultActionGroup(AnAction... actions) {
    this(null, false);
    for (AnAction action : actions) {
      add(action);
    }
  }

  public DefaultActionGroup(String shortName, boolean popup){
    super(shortName, popup);
  }

  /**
   * Adds the specified action to the tail.
   *
   * @param action Action to be added
   * @param actionManager ActionManager instance
   */
  public final void add(@NotNull AnAction action, @NotNull ActionManager actionManager){
    add(action, new Constraints(Anchor.LAST, null), actionManager);
  }

  public final void add(@NotNull AnAction action){
    addAction(action, new Constraints(Anchor.LAST, null));
  }

  public final ActionInGroup addAction(@NotNull AnAction action){
    return addAction(action, new Constraints(Anchor.LAST, null));
  }

  /**
   * Adds a separator to the tail.
   */
  public final void addSeparator(){
    add(Separator.getInstance());
  }

  /**
   * Adds the specified action with the specified constraint.
   *
   * @param action Action to be added; cannot be null
   * @param constraint Constraint to be used for determining action's position; cannot be null
   * @throws IllegalArgumentException in case when:
   * <li>action is null
   * <li>constraint is null
   * <li>action is already in the group
   */
  public final void add(@NotNull AnAction action, @NotNull Constraints constraint){
    add(action, constraint, ActionManager.getInstance());
  }

  public final ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint){
    return addAction(action, constraint, ActionManager.getInstance());
  }

  public final void add(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    addAction(action, constraint, actionManager);
  }

  public final ActionInGroup addAction(@NotNull AnAction action, @NotNull Constraints constraint, @NotNull ActionManager actionManager) {
    if (action == this) {
      throw new IllegalArgumentException("Cannot add a group to itself");
    }
    // Check that action isn't already registered
    if (!(action instanceof Separator)) {
      if (mySortedChildren.contains(action)) {
        throw new IllegalArgumentException("cannot add an action twice: " + action);
      }
      for (Pair<AnAction, Constraints> pair : myPairs) {
        if (action.equals(pair.first)) {
          throw new IllegalArgumentException("cannot add an action twice: " + action);
        }
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
        myPairs.add(new Pair<AnAction, Constraints>(action, constraint));
      }
    }

    return new ActionInGroup(this, action);
  }

  private void actionAdded(AnAction addedAction, ActionManager actionManager){
    String addedActionId = addedAction instanceof ActionStub ? ((ActionStub)addedAction).getId() : actionManager.getId(addedAction);
    if (addedActionId == null){
      return;
    }
    outer:
    while(!myPairs.isEmpty()){
      for(int i = 0; i < myPairs.size(); i++){
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (addToSortedList(pair.first, pair.second, actionManager)){
          myPairs.remove(i);
          continue outer;
        }
      }
      break;
    }
  }

  private boolean addToSortedList(AnAction action, Constraints constraint, ActionManager actionManager){
    int index = findIndex(constraint.myRelativeToActionId, mySortedChildren, actionManager);
    if (index == -1){
      return false;
    }
    if (constraint.myAnchor == Anchor.BEFORE){
      mySortedChildren.add(index, action);
    }
    else{
      mySortedChildren.add(index + 1, action);
    }
    return true;
  }

  private static int findIndex(String actionId, List<AnAction> actions, ActionManager actionManager) {
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
  public final void remove(AnAction action){
    if (!mySortedChildren.remove(action)){
      for(int i = 0; i < myPairs.size(); i++){
        Pair<AnAction, Constraints> pair = myPairs.get(i);
        if (pair.first.equals(action)){
          myPairs.remove(i);
          break;
        }
      }
    }
  }

  /**
   * Removes all children actions (separators as well) from the group.
   */
  public final void removeAll(){
    mySortedChildren.clear();
    myPairs.clear();
  }

  /**
   * Returns group's children in the order determined by constraints.
   *
   * @param e not used
   *
   * @return An array of children actions
   */
  @NotNull
  public final AnAction[] getChildren(@Nullable AnActionEvent e){
    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPairs.size()];
    for(int i = 0; i < sortedSize; i++){
      AnAction action = mySortedChildren.get(i);
      if (action instanceof ActionStub) {
        action = unstub(e, (ActionStub)action);
        mySortedChildren.set(i, action);
      }

      children[i] = action;
    }
    for(int i = 0; i < myPairs.size(); i++){
      final Pair<AnAction, Constraints> pair = myPairs.get(i);
      AnAction action = pair.first;
      if (action instanceof ActionStub) {
        action = unstub(e, (ActionStub)action);
        myPairs.set(i, Pair.create(action, pair.second));
      }

      children[i + sortedSize] = action;
    }
    return children;
  }

  private AnAction unstub(AnActionEvent e, final ActionStub stub) {
    ActionManager actionManager = e != null ? e.getActionManager() : ActionManager.getInstance();
    AnAction action = actionManager.getAction(stub.getId());
    replace(stub, action);
    return action;
  }

  /**
   * Returns the number of contained children (including separators).
   *
   * @return number of children in the group
   */
  public final int getChildrenCount(){
    return mySortedChildren.size() + myPairs.size();
  }

  @NotNull
  public final AnAction[] getChildActionsOrStubs(){
    // Mix sorted actions and pairs
    int sortedSize = mySortedChildren.size();
    AnAction[] children = new AnAction[sortedSize + myPairs.size()];
    for(int i = 0; i < sortedSize; i++){
      children[i] = mySortedChildren.get(i);
    }
    for(int i = 0; i < myPairs.size(); i++){
      children[i + sortedSize] = myPairs.get(i).first;
    }    
    return children;
  }

  public final void addAll(ActionGroup group) {
    for (AnAction each : group.getChildren(null)) {
      add(each);
    }
  }

  public final void addAll(AnAction... actions) {
    for (AnAction each : actions) {
      add(each);
    }
  }

  public void addSeparator(@Nullable String separatorText) {
    add(new Separator(separatorText));
  }
}
