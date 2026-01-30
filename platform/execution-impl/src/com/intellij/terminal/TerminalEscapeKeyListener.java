// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.toolWindow.InternalDecoratorImpl;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.Collection;

/**
 * Moves focus to editor on Escape key pressed, similarly to {@link InternalDecoratorImpl#processKeyBinding}.
 */
@ApiStatus.Internal
public final class TerminalEscapeKeyListener {
  private final @NotNull JBTerminalPanel myTerminalPanel;
  private final @Nullable AnAction myTerminalSwitchFocusToEditorAction;

  public TerminalEscapeKeyListener(@NotNull JBTerminalPanel terminalPanel) {
    myTerminalPanel = terminalPanel;
    myTerminalSwitchFocusToEditorAction = ActionManager.getInstance().getAction("Terminal.SwitchFocusToEditor");
  }

  public void handleKeyEvent(@NotNull KeyEvent e) {
    Project project = myTerminalPanel.getContextProject();
    if (e.getID() == KeyEvent.KEY_PRESSED && !e.isConsumed()
        && project != null && !project.isDisposed()
        && shouldSwitchFocusToEditor(e)) {
      ToolWindowManager.getInstance(project).activateEditorComponent();
      e.consume();
    }
  }

  private boolean shouldSwitchFocusToEditor(@NotNull KeyEvent e) {
    ToolWindow toolWindow = myTerminalPanel.getContextToolWindow();
    if (toolWindow == null) {
      // We are not in the tool window, so where we are? Maybe in the editor already.
      return false;
    }
    else if (myTerminalSwitchFocusToEditorAction != null) {
      Collection<KeyStroke> strokes = KeymapUtil.getKeyStrokes(myTerminalSwitchFocusToEditorAction.getShortcutSet());
      if (JBTerminalWidget.isTerminalToolWindow(toolWindow)) {
        // If we are in the terminal tool window, allow moving focus only if it matches the defined shortcut.
        return isMatched(e, strokes);
      }
      else {
        // This terminal panel is located out of the terminal tool window.
        // For example, it can be an execution console.
        // Let's follow the terminal shortcut if it is defined, but if it is not, then allow moving focus by Escape.
        return strokes.isEmpty() ? isEscape(e) : isMatched(e, strokes);
      }
    }
    else {
      // If there is no TerminalSwitchFocusToEditorAction, then it means that the Terminal plugin is unloaded.
      // And this terminal panel is located outside the terminal tool window.
      // So, let's allow moving focus to the editor if it is an escape key.
      return isEscape(e);
    }
  }

  private static boolean isMatched(@NotNull KeyEvent e, @NotNull Collection<KeyStroke> strokes) {
    return ContainerUtil.exists(strokes, stroke -> {
      //noinspection MagicConstant
      return stroke.getKeyCode() == e.getKeyCode() && stroke.getModifiers() == UIUtil.getAllModifiers(e);
    });
  }

  private static boolean isEscape(@NotNull KeyEvent e) {
    return e.getKeyCode() == KeyEvent.VK_ESCAPE && e.getModifiersEx() == 0;
  }
}
