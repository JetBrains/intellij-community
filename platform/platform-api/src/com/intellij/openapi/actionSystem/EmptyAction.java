// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * The purpose of this class is to reserve action-id in a {@code plugin.xml}, so the action appears in <em>Preferences | Keymap</em>.
 * Then Keymap assignments can be used for non-registered actions created at runtime.
 * <p>
 * Another usage is to override (hide) already registered actions via {@code plugin.xml}, see {@link EmptyActionGroup} as well.
 * <p>
 * It must never be shown in the UI.
 *
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 * @see EmptyActionGroup
 */
public final class EmptyAction extends AnAction {
  private final boolean myEnabled;

  /** Always hidden in the UI! */
  public EmptyAction() {
    this(false);
  }

  /** Do not use this in the UI! */
  public EmptyAction(boolean enabled) {
    myEnabled = enabled;
  }

  public static @NotNull AnAction createEmptyAction(@Nullable @Nls(capitalization = Nls.Capitalization.Title) String name,
                                                    @Nullable Icon icon,
                                                    boolean alwaysEnabled) {
    EmptyAction emptyAction = new EmptyAction(alwaysEnabled);
    if (name != null || icon != null) {
      emptyAction.getTemplatePresentation().setText(name);
      emptyAction.getTemplatePresentation().setIcon(icon);
    }
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

  /** @deprecated Use {@link ActionUtil#wrap(String)} and {@link AnAction#registerCustomShortcutSet} directly. */
  @Deprecated(forRemoval = true)
  public static @NotNull AnAction registerWithShortcutSet(@NotNull String id, @NotNull ShortcutSet shortcutSet, @NotNull JComponent component) {
    AnAction newAction = wrap(ActionManager.getInstance().getAction(id));
    newAction.registerCustomShortcutSet(shortcutSet, component);
    return newAction;
  }

  /** @deprecated Use {@link ActionUtil#wrap(AnAction)}, or {@link ActionGroupWrapper} and {@link AnActionWrapper} directly */
  @Deprecated(forRemoval = true)
  public static AnAction wrap(AnAction action) {
    return action instanceof ActionGroup ?
           new ActionGroupWrapper(((ActionGroup)action)) :
           new MyDelegatingAction(action);
  }

  /** @deprecated Use {@link AnActionWrapper} instead. */
  @Deprecated(forRemoval = true)
  public static class MyDelegatingAction extends AnActionWrapper {
    public MyDelegatingAction(@NotNull AnAction action) {
      super(action);
    }
  }
}
