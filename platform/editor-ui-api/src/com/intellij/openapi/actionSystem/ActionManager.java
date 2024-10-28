// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.ActionCallback;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.util.List;

/**
 * A manager for actions.
 * Used to register and unregister actions, it also contains utility methods to easily fetch action by id and id by action.
 *
 * @see AnAction
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/basic-action-system.html#buildingToolbarPopupMenu">Building a Toolbar/Popup Menu from Actions (IntelliJ Platform Docs)</a>
 */
public abstract class ActionManager {
  /**
   * Fetches the instance of ActionManager implementation.
   */
  public static ActionManager getInstance() {
    return ApplicationManager.getApplication().getService(ActionManager.class);
  }

  /**
   * Factory method that creates an {@code ActionPopupMenu} from the
   * specified group. The specified place is associated with the created popup.
   *
   * @param place Determines the place that will be set for {@link AnActionEvent} passed
   *              when an action from the group is either performed or updated
   *              See {@link com.intellij.openapi.actionSystem.ActionPlaces}
   * @param group Group from which the actions for the menu are taken.
   * @return An instance of {@code ActionPopupMenu}
   */
  public abstract @NotNull ActionPopupMenu createActionPopupMenu(@NonNls @NotNull String place, @NotNull ActionGroup group);

  /**
   * Factory method that creates an {@code ActionToolbar} from the
   * specified group. The specified place is associated with the created toolbar.
   *
   * @param place      Determines the place that will be set for {@link AnActionEvent} passed
   *                   when an action from the group is either performed or updated.
   *                   See {@link com.intellij.openapi.actionSystem.ActionPlaces}
   * @param group      Group from which the actions for the toolbar are taken.
   * @param horizontal The orientation of the toolbar ({@code true} - horizontal, {@code false} - vertical)
   * @return An instance of {@code ActionToolbar}
   */
  public abstract @NotNull ActionToolbar createActionToolbar(@NonNls @NotNull String place, @NotNull ActionGroup group, boolean horizontal);

  /**
   * Returns action associated with the specified actionId.
   *
   * @param actionId Id of the registered action
   * @return Action associated with the specified actionId, {@code null} if
   * there are no actions associated with the specified actionId
   * @throws IllegalArgumentException if {@code actionId} is {@code null}
   * @see com.intellij.openapi.actionSystem.IdeActions
   */
  public abstract AnAction getAction(@NonNls @NotNull String actionId);

  /**
   * Returns actionId associated with the specified action.
   *
   * @return id associated with the specified action, {@code null} if action is not registered
   * @throws IllegalArgumentException if {@code action} is {@code null}
   */
  public abstract @NonNls @Nullable String getId(@NotNull AnAction action);

  /**
   * Registers the specified action with the specified id. Note that the IDE's keymaps processing deals only with registered actions.
   *
   * @param actionId Id to associate with the action
   * @param action   Action to register
   */
  public abstract void registerAction(@NonNls @NotNull String actionId, @NotNull AnAction action);

  /**
   * Registers the specified action with the specified id.
   *
   * @param actionId Id to associate with the action
   * @param action   Action to register
   * @param pluginId The identifier of the plugin owning the action. Used to show the actions in the
   *                 correct place under the "Plugins" node in the "Keymap" settings pane and similar dialogs.
   */
  public abstract void registerAction(@NotNull String actionId, @NotNull AnAction action, @Nullable PluginId pluginId);

  /**
   * Unregisters the action with the specified actionId. <strong>If you're going to register another action with the same ID, use {@link #replaceAction(String, AnAction)}
   * instead</strong>, otherwise references in action groups may not be replaced.
   *
   * @param actionId Id of the action to be unregistered
   */
  public abstract void unregisterAction(@NotNull String actionId);

  /**
   * Replaces an existing action with ID {@code actionId} by {@code newAction}. Using this method for changing behavior of a platform action
   * is not recommended, extract an extension point in the action implementation instead.
   */
  public abstract void replaceAction(@NotNull String actionId, @NotNull AnAction newAction);

  /**
   * @deprecated Use {@link #getActionIdList}
   */
  @Deprecated
  public abstract String @NotNull [] getActionIds(@NotNull String idPrefix);

  /**
   * Returns the list of all registered action IDs with the specified prefix.
   */
  public abstract @NotNull List<@NonNls String> getActionIdList(@NotNull String idPrefix);

  /**
   * Checks if the specified action ID represents an action group and not an individual action.
   * Calling this method does not cause instantiation of a specific action class corresponding
   * to the action ID.
   *
   * @param actionId the ID to check.
   * @return {@code true} if the ID represents an action group, {@code false} otherwise.
   */
  public abstract boolean isGroup(@NotNull String actionId);

  public abstract @Nullable AnAction getActionOrStub(@NotNull @NonNls String id);

  public abstract void addTimerListener(@NotNull TimerListener listener);

  public abstract void removeTimerListener(@NotNull TimerListener listener);

  public abstract @NotNull ActionCallback tryToExecute(@NotNull AnAction action,
                                                       @Nullable InputEvent inputEvent,
                                                       @Nullable Component contextComponent,
                                                       @Nullable String place,
                                                       boolean now);

  /**
   * @deprecated Use {@link AnActionListener#TOPIC}
   */
  @Deprecated(forRemoval = true)
  public abstract void addAnActionListener(AnActionListener listener);

  /**
   * @deprecated Use {@link AnActionListener#TOPIC}
   */
  @Deprecated(forRemoval = true)
  public void addAnActionListener(AnActionListener listener, Disposable parentDisposable) {
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(AnActionListener.TOPIC, listener);
  }

  public abstract @Nullable KeyboardShortcut getKeyboardShortcut(@NonNls @NotNull String actionId);

  @ApiStatus.Internal
  protected ActionManager() {
  }
}
