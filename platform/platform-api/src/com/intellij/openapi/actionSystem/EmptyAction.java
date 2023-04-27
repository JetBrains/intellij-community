// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The purpose of this class is to reserve action-id in a {@code plugin.xml}, so the action appears in <em>Preferences | Keymap</em>.
 * Then Keymap assignments can be used for non-registered actions created at runtime.
 * <p>
 * Another usage is to override (hide) already registered actions via {@code plugin.xml}, see {@link EmptyActionGroup} as well.
 *
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 * @see EmptyActionGroup
 */
public final class EmptyAction extends AnAction {
  private boolean myEnabled;

  public EmptyAction() {
  }

  public EmptyAction(boolean enabled) {
    myEnabled = enabled;
  }

  public EmptyAction(@Nullable @NlsActions.ActionText String text,
                     @Nullable @NlsActions.ActionDescription String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public static AnAction createEmptyAction(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String name,
                                           @Nullable Icon icon,
                                           boolean alwaysEnabled) {
    final EmptyAction emptyAction = new EmptyAction(name, null, icon);
    emptyAction.myEnabled = alwaysEnabled;
    return emptyAction;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(myEnabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  public static void setupAction(@NotNull AnAction action, @NotNull String id, @Nullable JComponent component) {
    ActionUtil.mergeFrom(action, id).registerCustomShortcutSet(component, null);
  }

  public static void registerActionShortcuts(@NotNull JComponent component, @NotNull JComponent fromComponent) {
    ActionUtil.copyRegisteredShortcuts(component, fromComponent);
  }

  /**
   * Registers global action on a component with a custom shortcut set.
   * <p>
   * ActionManager.getInstance().getAction(id).registerCustomShortcutSet(shortcutSet, component) shouldn't be used directly,
   * because it will erase shortcuts, assigned to this action in keymap.
   */
  @NotNull
  public static AnAction registerWithShortcutSet(@NotNull String id, @NotNull ShortcutSet shortcutSet, @NotNull JComponent component) {
    AnAction newAction = wrap(ActionManager.getInstance().getAction(id));
    newAction.registerCustomShortcutSet(shortcutSet, component);
    return newAction;
  }

  /**
   * Creates proxy action
   * <p>
   * It allows altering template presentation and shortcut set without affecting the original action.
   */
  public static AnAction wrap(final AnAction action) {
    return action instanceof ActionGroup ?
           new MyDelegatingActionGroup(((ActionGroup)action)) :
           new MyDelegatingAction(action);
  }

  /**
   * @deprecated Use {@link AnActionWrapper} instead.
   */
  @Deprecated(forRemoval = true)
  public static class MyDelegatingAction extends AnActionWrapper {
    public MyDelegatingAction(@NotNull AnAction action) {
      super(action);
    }
  }

  /**
   * @deprecated Use {@link ActionGroupWrapper} instead.
   */
  @Deprecated(forRemoval = true)
  public static class MyDelegatingActionGroup extends ActionGroupWrapper {

    public MyDelegatingActionGroup(@NotNull ActionGroup group) {
      super(group);
    }
  }
}
