// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.terminal;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.toolWindow.InternalDecoratorImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;

/**
 * Moves focus to editor on Escape key pressed, similarly to {@link InternalDecoratorImpl#processKeyBinding}.
 */
public class TerminalEscapeKeyListener {
  private final JBTerminalPanel myTerminalPanel;
  private final AnAction myTerminalSwitchFocusToEditorAction;

  public TerminalEscapeKeyListener(@NotNull JBTerminalPanel terminalPanel) {
    myTerminalPanel = terminalPanel;
    myTerminalSwitchFocusToEditorAction = ActionManager.getInstance().getAction("Terminal.SwitchFocusToEditor");
  }

  public void handleKeyEvent(@NotNull KeyEvent e) {
    if (e.getID() == KeyEvent.KEY_PRESSED && !e.isConsumed() && isMatched(e) && switchFocusToEditorIfSuitable()) {
      e.consume();
    }
  }

  private boolean isMatched(@NotNull KeyEvent e) {
    KeyStroke stroke = getKeyStroke();
    return stroke != null && stroke.getKeyCode() == e.getKeyCode() &&
           stroke.getModifiers() == (e.getModifiers() | e.getModifiersEx());
  }

  @Nullable
  private KeyStroke getKeyStroke() {
    if (myTerminalSwitchFocusToEditorAction == null) {
      return null;
    }
    return KeymapUtil.getKeyStroke(myTerminalSwitchFocusToEditorAction.getShortcutSet());
  }

  private boolean switchFocusToEditorIfSuitable() {
    if (shouldSwitchFocusToEditor()) {
      Project project = myTerminalPanel.getContextProject();
      if (project != null && !project.isDisposed()) {
        // Repeat logic from com.intellij.openapi.wm.impl.InternalDecorator#processKeyBinding
        ToolWindowManager.getInstance(project).activateEditorComponent();
        return true;
      }
    }
    return false;
  }

  private boolean shouldSwitchFocusToEditor() {
    if (myTerminalPanel.getTerminalTextBuffer().isUsingAlternateBuffer()) {
      return false;
    }
    ToolWindow toolWindow = myTerminalPanel.getContextToolWindow();
    if (toolWindow == null) {
      return false;
    }
    if (JBTerminalWidget.isTerminalToolWindow(toolWindow) && !AdvancedSettings.getBoolean("terminal.escape.moves.focus.to.editor")) {
      return false; // For example, vi key bindings configured in terminal
    }
    return true;
  }
}
