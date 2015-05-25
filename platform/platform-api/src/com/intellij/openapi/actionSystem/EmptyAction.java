/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.ex.ActionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * This class purpose is to reserve action-id in a plugin.xml so the action appears in Keymap.
 * Then Keymap assignments can be used for non-registered actions created on runtime.
 *
 * Another usage is to override (hide) already registered actions by means of plugin.xml, see {@link EmptyActionGroup} as well.
 *
 * @see EmptyActionGroup
 *
 * @author Gregory.Shrago
 * @author Konstantin Bulenkov
 */
public final class EmptyAction extends AnAction {
  private boolean myEnabled;

  public EmptyAction() {
  }

  public EmptyAction(boolean enabled) {
    myEnabled = enabled;
  }

  public EmptyAction(@Nullable String text, @Nullable String description, @Nullable Icon icon) {
    super(text, description, icon);
  }

  public static AnAction createEmptyAction(@Nullable String name, @Nullable Icon icon, boolean alwaysEnabled) {
    final EmptyAction emptyAction = new EmptyAction(name, null, icon);
    emptyAction.myEnabled = alwaysEnabled;
    return emptyAction;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(myEnabled);
  }

  public static void setupAction(@NotNull AnAction action, @NotNull String id, @Nullable JComponent component) {
    final AnAction emptyAction = ActionManager.getInstance().getAction(id);
    final Presentation copyFrom = emptyAction.getTemplatePresentation();
    final Presentation copyTo = action.getTemplatePresentation();
    if (copyTo.getIcon() == null) {
      copyTo.setIcon(copyFrom.getIcon());
    }
    copyTo.setText(copyFrom.getText());
    copyTo.setDescription(copyFrom.getDescription());
    action.registerCustomShortcutSet(emptyAction.getShortcutSet(), component);
  }

  public static void registerActionShortcuts(JComponent component, final JComponent fromComponent) {
    for (AnAction anAction : ActionUtil.getActions(fromComponent)) {
      anAction.registerCustomShortcutSet(anAction.getShortcutSet(), component);
    }
  }

  public static void registerWithShortcutSet(@NotNull String id, @NotNull ShortcutSet shortcutSet, @NotNull JComponent component) {
    AnAction newAction = wrap(ActionManager.getInstance().getAction(id));
    newAction.registerCustomShortcutSet(shortcutSet, component);
  }

  public static AnAction wrap(final AnAction action) {
    return action instanceof ActionGroup ?
           new MyDelegatingActionGroup(((ActionGroup)action)) :
           new MyDelegatingAction(action);
  }

  private static class MyDelegatingAction extends AnAction {
    @NotNull private final AnAction myDelegate;

    public MyDelegatingAction(@NotNull AnAction action) {
      myDelegate = action;
      copyFrom(action);
      setEnabledInModalContext(action.isEnabledInModalContext());
    }

    @Override
    public void update(final AnActionEvent e) {
      myDelegate.update(e);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      myDelegate.actionPerformed(e);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public boolean isTransparentUpdate() {
      return myDelegate.isTransparentUpdate();
    }

    @Override
    public boolean isInInjectedContext() {
      return myDelegate.isInInjectedContext();
    }
  }

  private static class MyDelegatingActionGroup extends ActionGroup {
    @NotNull private final ActionGroup myDelegate;

    public MyDelegatingActionGroup(@NotNull ActionGroup action) {
      myDelegate = action;
      copyFrom(action);
      setEnabledInModalContext(action.isEnabledInModalContext());
    }

    @Override
    public boolean isPopup() {
      return myDelegate.isPopup();
    }

    @NotNull
    @Override
    public AnAction[] getChildren(@Nullable final AnActionEvent e) {
      return myDelegate.getChildren(e);
    }

    @Override
    public void update(final AnActionEvent e) {
      myDelegate.update(e);
    }

    @Override
    public boolean canBePerformed(DataContext context) {
      return myDelegate.canBePerformed(context);
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      myDelegate.actionPerformed(e);
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public boolean isTransparentUpdate() {
      return myDelegate.isTransparentUpdate();
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
}
