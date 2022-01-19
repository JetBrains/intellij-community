// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.EventLog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowEventSource;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SizedIcon;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Toggles tool window visibility.
 * Usually shown in View|Tool-windows sub-menu.
 * Dynamically registered in Settings|Keymap for each newly-registered tool window.
 */
public class ActivateToolWindowAction extends DumbAwareAction {
  private final String myToolWindowId;

  protected ActivateToolWindowAction(@NotNull String toolWindowId) {
    myToolWindowId = toolWindowId;
  }

  public @NotNull String getToolWindowId() {
    return myToolWindowId;
  }

  public static void ensureToolWindowActionRegistered(@NotNull ToolWindow toolWindow, @NotNull ActionManager actionManager) {
    String actionId = getActionIdForToolWindow(toolWindow.getId());
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      ActivateToolWindowAction newAction = new ActivateToolWindowAction(toolWindow.getId());
      newAction.updatePresentation(newAction.getTemplatePresentation(), toolWindow);
      actionManager.registerAction(actionId, newAction);
    }
  }

  public static void unregister(@NotNull String id) {
    String actionId = getActionIdForToolWindow(id);
    ActionManager actionManager = ActionManager.getInstance();
    if (actionManager.getAction(actionId) != null) {
      actionManager.unregisterAction(actionId);
    }
  }

  public static void updateToolWindowActionPresentation(@NotNull ToolWindow toolWindow) {
    AnAction action = ActionManager.getInstance().getAction(getActionIdForToolWindow(toolWindow.getId()));
    if (action instanceof ActivateToolWindowAction) {
      ((ActivateToolWindowAction)action).updatePresentation(action.getTemplatePresentation(), toolWindow);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId);
    if (toolWindow == null) {
      presentation.setEnabledAndVisible(hasEmptyState());
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(toolWindow.isAvailable() || hasEmptyState());
      updatePresentation(presentation, toolWindow);
    }
  }

  protected boolean hasEmptyState() {
    return false;
  }

  private void updatePresentation(@NotNull Presentation presentation, @NotNull ToolWindow toolWindow) {
    String title = toolWindow.getStripeTitle();
    presentation.setText(title);
    presentation.setDescription(IdeBundle.messagePointer("action.activate.tool.window", title));
    Icon icon = toolWindow.getIcon();
    if (EventLog.LOG_TOOL_WINDOW_ID.equals(myToolWindowId)) {
      icon = AllIcons.Ide.Notification.InfoEvents;
    }
    if (ExperimentalUI.isNewUI() && icon instanceof ScalableIcon) {
      icon = ((ScalableIcon)icon).scale(16f / icon.getIconWidth());
      presentation.setIcon(icon);
      return;
    }
    presentation.setIcon(icon == null ? null : new SizedIcon(icon, icon.getIconHeight(), icon.getIconHeight()));
  }

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
    Project project = getEventProject(e);
    if (project == null) {
      return;
    }

    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);

    ToolWindowEventSource source;
    if (e.getInputEvent() instanceof KeyEvent) {
      source = ToolWindowEventSource.ActivateActionKeyboardShortcut;
    }
    else if (ActionPlaces.MAIN_MENU.equals(e.getPlace())) {
      source = ToolWindowEventSource.ActivateActionMenu;
    }
    else if (ActionPlaces.ACTION_SEARCH.equals(e.getPlace())) {
      source = ToolWindowEventSource.ActivateActionGotoAction;
    }
    else {
      source = ToolWindowEventSource.ActivateActionOther;
    }
    if (windowManager.isEditorComponentActive() || !myToolWindowId.equals(windowManager.getActiveToolWindowId())) {
      ToolWindow toolWindow = windowManager.getToolWindow(myToolWindowId);
      if (toolWindow != null) {
        if (hasEmptyState() && !toolWindow.isAvailable()) {
          toolWindow.setAvailable(true);
        }
        if (windowManager instanceof ToolWindowManagerImpl) {
          ((ToolWindowManagerImpl) windowManager).activateToolWindow(myToolWindowId, null, true, source);
        }
        else {
          toolWindow.activate(null);
        }
      }
      else if (hasEmptyState()) {
        createEmptyState(project);
      }
    }
    else {
      if (windowManager instanceof ToolWindowManagerImpl) {
        ((ToolWindowManagerImpl) windowManager).hideToolWindow(myToolWindowId, false, true, false, source);
      }
      else {
        ToolWindow toolWindow = windowManager.getToolWindow(myToolWindowId);
        if (toolWindow != null) {
          toolWindow.hide(null);
        }
      }
    }
  }

  protected void createEmptyState(Project project) {
  }

  /**
   * This is the "rule" method constructs {@code ID} of the action for activating tool window
   * with specified {@code ID}.
   *
   * @param id {@code id} of tool window to be activated.
   */
  @NonNls
  public static @NotNull String getActionIdForToolWindow(@NotNull String id) {
    return "Activate" + id.replaceAll(" ", "") + "ToolWindow";
  }

  /**
   * @return mnemonic for action if it has Alt+digit/Meta+digit shortcut.
   * Otherwise the method returns {@code -1}. Meta mask is OK for
   * Mac OS X user, because Alt+digit types strange characters into the
   * editor.
   */
  public static int getMnemonicForToolWindow(@NotNull String toolWindowId) {
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    for (Shortcut shortcut : activeKeymap.getShortcuts(getActionIdForToolWindow(toolWindowId))) {
      if (!(shortcut instanceof KeyboardShortcut)) {
        continue;
      }

      KeyStroke keyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
      int modifiers = keyStroke.getModifiers();
      if (modifiers == (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK) ||
          modifiers == InputEvent.ALT_MASK ||
          modifiers == InputEvent.ALT_DOWN_MASK ||
          modifiers == (InputEvent.META_DOWN_MASK | InputEvent.META_MASK) ||
          modifiers == InputEvent.META_MASK ||
          modifiers == InputEvent.META_DOWN_MASK) {
        int keyCode = keyStroke.getKeyCode();
        if (KeyEvent.VK_0 <= keyCode && keyCode <= KeyEvent.VK_9) {
          return (char)('0' + keyCode - KeyEvent.VK_0);
        }
      }
    }
    return -1;
  }
}
