/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.ApplicationManager;

/**
 * A manager for actions. Used to register and unregister actions, also
 * contains util methods to easily fetch action by id and id by action.
 */
public abstract class ActionManager {

  /**
   * Fetches the instance of ActionManager implementation.
   */
  public static ActionManager getInstance(){
    return ApplicationManager.getApplication().getComponent(ActionManager.class);
  }

  /**
   * Factory method that creates an <code>ActionPopupMenu</code> from the
   * specified group. The specified place is associated with the created popup.
   *
   * @param place Determines the place that will be set for {@link AnActionEvent} passed
   *  when an action from the group is either performed or updated
   *
   * @param group Group to associate with the created popup menu
   *
   * @return An instance of <code>ActionPopupMenu</code>
   */
  public abstract ActionPopupMenu createActionPopupMenu(String place, ActionGroup group);

  /**
   * Factory method that creates an <code>ActionToolbar</code> from the
   * specified group. The specified place is associated with the created toolbar.
   *
   * @param place Determines the place that will be set for {@link AnActionEvent} passed
   *  when an action from the group is either performed or updated
   *
   * @param group Group to associate with the created toolbar
   *
   * @return An instance of <code>ActionToolbar</code>
   */
  public abstract ActionToolbar createActionToolbar(String place, ActionGroup group, boolean horizontal);

  /**
   * Returns action associated with the specified actionId.
   *
   * @param actionId Id of the registered action
   *
   * @return Action associated with the specified actionId, <code>null</code> if
   *  there is no actions associated with the speicified actionId
   *
   * @exception java.lang.IllegalArgumentException if <code>actionId</code> is <code>null</code>
   */
  public abstract AnAction getAction(String actionId);

  /**
   * Returns actionId associated with the specified action.
   *
   * @return id associated with the specified action, <code>null</code> if action
   *  is not registered
   *
   * @exception java.lang.IllegalArgumentException if <code>action</code> is <code>null</code>
   */
  public abstract String getId(AnAction action);

  /**
   * Registers the specified action with the specified id. Note that IDEA's keymaps
   * processing deals only with registered actions.
   *
   * @param actionId Id to associate with the action
   * @param action Action to register
   */
  public abstract void registerAction(String actionId, AnAction action);

  /**
   * Unregisters the action with the specified actionId.
   *
   * @param actionId Id of the action to be unregistered
   */
  public abstract void unregisterAction(String actionId);

}
