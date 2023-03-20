// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

import com.jediterm.terminal.ui.TerminalPanel;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

public final class TerminalUtils {

  private TerminalUtils() { }

  public static boolean isTerminalComponent(@Nullable Component component) {
    return component instanceof TerminalPanel;
  }

  public static boolean hasSelectionInTerminal(@Nullable Component component) {
    return component instanceof TerminalPanel terminalPanel && terminalPanel.getSelection() != null;
  }

  public static @Nullable String getSelectedTextInTerminal(@Nullable Component component) {
    return component instanceof TerminalPanel panel ? JBTerminalWidget.getSelectedText(panel) : null;
  }

  public static @Nullable String getTextInTerminal(@Nullable Component component) {
    return component instanceof TerminalPanel panel ? JBTerminalWidget.getText(panel) : null;
  }
}
