// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.MainMenuPresentationAware;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.impl.ToolWindowManagerImpl;
import com.intellij.toolWindow.ToolWindowEventSource;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SizedIcon;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.concurrency.SynchronizedClearableLazy;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.function.Supplier;

/**
 * Toggles tool window visibility.
 * Usually shown in View|Tool-windows submenu.
 * Dynamically registered in Settings|Keymap for each newly registered tool window.
 */
public class ActivateToolWindowAction extends DumbAwareAction implements MainMenuPresentationAware, ActionRemoteBehaviorSpecification.Frontend {
  private final String myToolWindowId;

  protected ActivateToolWindowAction(@NotNull String toolWindowId) {
    myToolWindowId = toolWindowId;
  }

  public @NotNull String getToolWindowId() {
    return myToolWindowId;
  }

  @Override
  public boolean alwaysShowIconInMainMenu() {
    return true;
  }

  public static void ensureToolWindowActionRegistered(@NotNull ToolWindow toolWindow, @NotNull ActionManager actionManager) {
    String actionId = getActionIdForToolWindow(toolWindow.getId());
    AnAction action = actionManager.getAction(actionId);
    if (action == null) {
      actionManager.registerAction(actionId, new ActivateToolWindowAction(toolWindow.getId()));
      updateToolWindowActionPresentation(toolWindow);
    }
  }

  public static void unregister(@NotNull String id) {
    ActionManager.getInstance().unregisterAction(getActionIdForToolWindow(id));
  }

  public static void updateToolWindowActionPresentation(@NotNull ToolWindow toolWindow) {
    AnAction action = ActionManager.getInstance().getAction(getActionIdForToolWindow(toolWindow.getId()));
    if (action instanceof ActivateToolWindowAction) {
      updatePresentation(action.getTemplatePresentation(), toolWindow);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
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
      presentation.setEnabledAndVisible(hasEmptyState(project));
    }
    else {
      presentation.setVisible(true);
      boolean available = toolWindow.isAvailable() || hasEmptyState(project);
      if (e.getPlace().equals(ActionPlaces.POPUP)) {
        presentation.setVisible(available);
      }
      else {
        presentation.setEnabled(available);
      }

      updatePresentation(presentation, toolWindow);
    }
  }

  protected boolean hasEmptyState(@NotNull Project project) {
    return false;
  }

  private static void updatePresentation(@NotNull Presentation presentation, @NotNull ToolWindow toolWindow) {
    Supplier<@NlsContexts.TabTitle String> title = toolWindow.getStripeTitleProvider();
    presentation.setText(title);
    presentation.setDescription(() -> IdeBundle.message("action.activate.tool.window", title.get()));
    Icon toolWindowIcon = toolWindow.getIcon();
    presentation.setIconSupplier(new SynchronizedClearableLazy<>(() -> {
      Icon icon = toolWindowIcon;
      if (icon instanceof ScalableIcon && ExperimentalUI.isNewUI()) {
        icon = ((ScalableIcon)icon).scale(JBUIScale.scale(16f) / icon.getIconWidth());
        return icon;
      }
      return icon == null ? null : new SizedIcon(icon, icon.getIconHeight(), icon.getIconHeight());
    }));
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
        if (hasEmptyState(project) && !toolWindow.isAvailable()) {
          toolWindow.setAvailable(true);
        }
        if (windowManager instanceof ToolWindowManagerImpl) {
          ((ToolWindowManagerImpl)windowManager).activateToolWindow(myToolWindowId, null, true, source);
        }
        else {
          toolWindow.activate(null);
        }
      }
      else if (hasEmptyState(project)) {
        createEmptyState(project);
      }
    }
    else if (windowManager instanceof ToolWindowManagerImpl) {
      ((ToolWindowManagerImpl)windowManager).hideToolWindow(myToolWindowId, false, true, false, source);
    }
    else {
      ToolWindow toolWindow = windowManager.getToolWindow(myToolWindowId);
      if (toolWindow != null) {
        toolWindow.hide(null);
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
  public static @NonNls @NotNull String getActionIdForToolWindow(@NotNull String id) {
    return "Activate" + id.replaceAll(" ", "") + "ToolWindow";
  }

  /**
   * @return mnemonic for action if it has Alt+digit/Meta+digit shortcut.
   * Otherwise, the method returns {@code -1}.
   * Meta-mask is OK for Mac OS X user, because Alt+digit types strange characters into the editor.
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
