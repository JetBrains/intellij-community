/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;

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
     * Registers the specified action with the specified id.
     *
     * @param actionId Id to associate with the action
   * @param action Action to register
   */

  public abstract void registerAction(String actionId, AnAction action, PluginId pluginId);


  /**
   * Unregisters the action with the specified actionId.
   *
   * @param actionId Id of the action to be unregistered
   */
  public abstract void unregisterAction(String actionId);

}
