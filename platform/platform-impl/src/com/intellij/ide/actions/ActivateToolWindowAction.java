
/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.notification.EventLog;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.content.Content;
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

  private ActivateToolWindowAction(@NotNull String toolWindowId) {
    myToolWindowId = toolWindowId;
  }

  @NotNull
  public String getToolWindowId() {
    return myToolWindowId;
  }

  public static void ensureToolWindowActionRegistered(@NotNull ToolWindowImpl toolWindow) {
    ActionManager actionManager = ActionManager.getInstance();
    String actionId = getActionIdForToolWindow(toolWindow.getId());
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      ActivateToolWindowAction newAction = new ActivateToolWindowAction(toolWindow.getId());
      newAction.updatePresentation(newAction.getTemplatePresentation(), toolWindow);
      actionManager.registerAction(actionId, newAction);
    }
  }

  public static void updateToolWindowActionPresentation(@NotNull ToolWindowImpl toolWindow) {
    ActionManager actionManager = ActionManager.getInstance();
    String actionId = getActionIdForToolWindow(toolWindow.getId());
    AnAction action = actionManager.getAction(actionId);
    if (action instanceof ActivateToolWindowAction) {
      ((ActivateToolWindowAction)action).updatePresentation(action.getTemplatePresentation(), toolWindow);
    }
  }

  public void update(AnActionEvent e) {
    Project project = getEventProject(e);
    Presentation presentation = e.getPresentation();
    if (project == null || project.isDisposed()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(myToolWindowId);
    if (toolWindow == null) {
      presentation.setEnabledAndVisible(false);
    }
    else {
      presentation.setVisible(true);
      presentation.setEnabled(toolWindow.isAvailable());
      updatePresentation(presentation, toolWindow);
    }
  }

  private void updatePresentation(@NotNull Presentation presentation, @NotNull ToolWindow toolWindow) {
    String title = toolWindow.getStripeTitle();
    presentation.setText(title);
    presentation.setDescription(IdeBundle.message("action.activate.tool.window", title));
    Icon icon = toolWindow.getIcon();
    if (EventLog.LOG_TOOL_WINDOW_ID.equals(myToolWindowId)) {
      icon = AllIcons.Ide.Notification.InfoEvents;
    }
    presentation.setIcon(icon == null ? null : new SizedIcon(icon, icon.getIconHeight(), icon.getIconHeight()));
  }

  public void actionPerformed(final AnActionEvent e) {
    Project project = getEventProject(e);
    if (project == null) return;
    ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
    final ToolWindow window = windowManager.getToolWindow(myToolWindowId);
    InputEvent event = e.getInputEvent();
    Runnable run = null;
    if (event instanceof KeyEvent && event.isShiftDown()) {
      final Content[] contents = window.getContentManager().getContents();
      if (contents.length > 0 && window.getContentManager().getSelectedContent() != contents[0]) {
        run = () -> window.getContentManager().setSelectedContent(contents[0], true, true);
      }
    }

    if (windowManager.isEditorComponentActive() || !myToolWindowId.equals(windowManager.getActiveToolWindowId()) || run != null) {
      if (run != null && window.isActive()) {
        run.run();
      } else {
        window.activate(run);
      }
    } else {
      windowManager.getToolWindow(myToolWindowId).hide(null);
    }
  }

  /**
   * This is the "rule" method constructs {@code ID} of the action for activating tool window
   * with specified {@code ID}.
   *
   * @param id {@code id} of tool window to be activated.
   */
  @NonNls
  public static String getActionIdForToolWindow(String id) {
    return "Activate" + id.replaceAll(" ", "") + "ToolWindow";
  }

  /**
   * @return mnemonic for action if it has Alt+digit/Meta+digit shortcut.
   * Otherwise the method returns {@code -1}. Meta mask is OK for
   * Mac OS X user, because Alt+digit types strange characters into the
   * editor.
   */
  public static int getMnemonicForToolWindow(String id) {
    Keymap activeKeymap = KeymapManager.getInstance().getActiveKeymap();
    Shortcut[] shortcuts = activeKeymap.getShortcuts(getActionIdForToolWindow(id));
    for (Shortcut shortcut : shortcuts) {
      if (shortcut instanceof KeyboardShortcut) {
        KeyStroke keyStroke = ((KeyboardShortcut)shortcut).getFirstKeyStroke();
        int modifiers = keyStroke.getModifiers();
        if (
          modifiers == (InputEvent.ALT_DOWN_MASK | InputEvent.ALT_MASK) ||
          modifiers == InputEvent.ALT_MASK ||
          modifiers == InputEvent.ALT_DOWN_MASK ||
          modifiers == (InputEvent.META_DOWN_MASK | InputEvent.META_MASK) ||
          modifiers == InputEvent.META_MASK ||
          modifiers == InputEvent.META_DOWN_MASK
          ) {
          int keyCode = keyStroke.getKeyCode();
          if (KeyEvent.VK_0 <= keyCode && keyCode <= KeyEvent.VK_9) {
            char c = (char)('0' + keyCode - KeyEvent.VK_0);
            return (int)c;
          }
        }
      }
    }
    return -1;
  }
}
