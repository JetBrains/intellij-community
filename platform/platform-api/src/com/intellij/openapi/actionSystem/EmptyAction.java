// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.actionSystem;

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This class purpose is to reserve action-id in a plugin.xml so the action appears in Keymap.
 * Then Keymap assignments can be used for non-registered actions created on runtime.
 * <p>
 * Another usage is to override (hide) already registered actions by means of plugin.xml, see {@link EmptyActionGroup} as well.
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
   * It allows to alter template presentation and shortcut set without affecting original action,
   */
  public static AnAction wrap(final AnAction action) {
    return action instanceof ActionGroup ?
           new MyDelegatingActionGroup(((ActionGroup)action)) :
           new MyDelegatingAction(action);
  }

  public static class MyDelegatingAction extends AnAction implements ActionWithDelegate<AnAction> {
    @NotNull private final AnAction myDelegate;

    public MyDelegatingAction(@NotNull AnAction action) {
      myDelegate = action;
      copyFrom(action);
      setEnabledInModalContext(action.isEnabledInModalContext());
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      myDelegate.update(e);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      myDelegate.actionPerformed(e);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public boolean isInInjectedContext() {
      return myDelegate.isInInjectedContext();
    }

    @NotNull
    @Override
    public AnAction getDelegate() {
      return myDelegate;
    }
  }

  public static class MyDelegatingActionGroup extends ActionGroup {
    @NotNull private final ActionGroup myDelegate;

    public MyDelegatingActionGroup(@NotNull ActionGroup action) {
      myDelegate = action;
      copyFrom(action);
      setEnabledInModalContext(action.isEnabledInModalContext());
    }

    @NotNull
    public ActionGroup getDelegate() {
      return myDelegate;
    }

    @Override
    public boolean isPopup() {
      return myDelegate.isPopup();
    }

    @Override
    public AnAction @NotNull [] getChildren(@Nullable final AnActionEvent e) {
      return myDelegate.getChildren(e);
    }

    @Override
    public void update(@NotNull final AnActionEvent e) {
      myDelegate.update(e);
    }

    @Override
    public boolean canBePerformed(@NotNull DataContext context) {
      return myDelegate.canBePerformed(context);
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
      myDelegate.actionPerformed(e);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public boolean isInInjectedContext() {
      return myDelegate.isInInjectedContext();
    }

    @Override
    public boolean hideIfNoVisibleChildren() {
      return myDelegate.hideIfNoVisibleChildren();
    }

    @Override
    public boolean disableIfNoVisibleChildren() {
      return myDelegate.disableIfNoVisibleChildren();
    }
  }

  public static class DelegatingCompactActionGroup extends MyDelegatingActionGroup implements CompactActionGroup {
    public DelegatingCompactActionGroup(@NotNull ActionGroup action) {
      super(action);
    }
  }
}
