/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;

/**
 * A manager for actions. Used to register and unregister actions, also
 * contains utility methods to easily fetch action by id and id by action.
 *
 * @see AnAction
 */
public abstract class ActionManager {
  /**
   * Fetches the instance of ActionManager implementation.
   */
  public static ActionManager getInstance(){
    return ApplicationManager.getApplication().getComponent(ActionManager.class);
  }

  /**
   * Factory method that creates an {@code ActionPopupMenu} from the
   * specified group. The specified place is associated with the created popup.
   *
   * @param place Determines the place that will be set for {@link AnActionEvent} passed
   *  when an action from the group is either performed or updated
   *  See {@link com.intellij.openapi.actionSystem.ActionPlaces}
   *
   * @param group Group from which the actions for the menu are taken.
   *
   * @return An instance of {@code ActionPopupMenu}
   */
  @NotNull
  public abstract ActionPopupMenu createActionPopupMenu(@NonNls String place, @NotNull ActionGroup group);

  /**
   * Factory method that creates an {@code ActionToolbar} from the
   * specified group. The specified place is associated with the created toolbar.
   *
   * @param place Determines the place that will be set for {@link AnActionEvent} passed
   *  when an action from the group is either performed or updated.
   *  See {@link com.intellij.openapi.actionSystem.ActionPlaces}
   *
   * @param group Group from which the actions for the toolbar are taken.
   *
   * @param horizontal The orientation of the toolbar (true - horizontal, false - vertical)
   *
   * @return An instance of {@code ActionToolbar}
   */
  @NotNull
  public abstract ActionToolbar createActionToolbar(@NonNls String place, @NotNull ActionGroup group, boolean horizontal);

  /**
   * Returns action associated with the specified actionId.
   *
   * @param actionId Id of the registered action
   *
   * @return Action associated with the specified actionId, {@code null} if
   *  there is no actions associated with the specified actionId
   *
   * @exception java.lang.IllegalArgumentException if {@code actionId} is {@code null}
   *
   * @see com.intellij.openapi.actionSystem.IdeActions
   */
  public abstract AnAction getAction(@NonNls @NotNull String actionId);

  /**
   * Returns actionId associated with the specified action.
   *
   * @return id associated with the specified action, {@code null} if action
   *  is not registered
   *
   * @exception java.lang.IllegalArgumentException if {@code action} is {@code null}
   */
  public abstract String getId(@NotNull AnAction action);

  /**
   * Registers the specified action with the specified id. Note that IDEA's keymaps
   * processing deals only with registered actions.
   *
   * @param actionId Id to associate with the action
   * @param action Action to register
   */
  public abstract void registerAction(@NonNls @NotNull String actionId, @NotNull AnAction action);

  /**
   * Registers the specified action with the specified id.
   *
   * @param actionId Id to associate with the action
   * @param action   Action to register
   * @param pluginId Identifier of the plugin owning the action. Used to show the actions in the
   *                 correct place under the "Plugins" node in the "Keymap" settings pane and similar dialogs.
   */
  public abstract void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId);

  /**
   * Unregisters the action with the specified actionId.
   *
   * @param actionId Id of the action to be unregistered
   */
  public abstract void unregisterAction(@NotNull String actionId);

  /**
   * Returns the list of all registered action IDs with the specified prefix.
   *
   * @return all action {@code id}s which have the specified prefix.
   * @since 5.1
   */
  public abstract String[] getActionIds(@NotNull String idPrefix);

  /**
   * Checks if the specified action ID represents an action group and not an individual action.
   * Calling this method does not cause instantiation of a specific action class corresponding
   * to the action ID.
   *
   * @param actionId the ID to check.
   * @return true if the ID represents an action group, false otherwise.
   * @since 5.1
   */
  public abstract boolean isGroup(@NotNull String actionId);

  /**
   * Creates a panel with buttons which invoke actions from the specified action group.
   *
   * @param actionPlace        the place where the panel will be used (see {@link ActionPlaces}).
   * @param messageActionGroup the action group from which the toolbar is created.
   * @return the created panel.
   * @since 5.1
   */
  @NotNull
  public abstract JComponent createButtonToolbar(final String actionPlace, @NotNull ActionGroup messageActionGroup);

  @Nullable
  public abstract AnAction getActionOrStub(@NonNls String id);

  public abstract void addTimerListener(int delay, TimerListener listener);

  public abstract void removeTimerListener(TimerListener listener);

  public abstract void addTransparentTimerListener(int delay, TimerListener listener);

  public abstract void removeTransparentTimerListener(TimerListener listener);

  public abstract ActionCallback tryToExecute(@NotNull AnAction action, @NotNull InputEvent inputEvent, @Nullable Component contextComponent,
                                              @Nullable String place, boolean now);

  public abstract void addAnActionListener(AnActionListener listener);
  public abstract void addAnActionListener(AnActionListener listener, Disposable parentDisposable);

  public abstract void removeAnActionListener(AnActionListener listener);

  @Nullable
  public abstract KeyboardShortcut getKeyboardShortcut(@NonNls @NotNull String actionId);

  @NotNull
  public String getComponentName() {
    return "ActionManager";
  }
}
