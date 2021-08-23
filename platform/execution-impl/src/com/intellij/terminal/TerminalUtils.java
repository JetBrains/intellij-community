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
    if (!(component instanceof TerminalPanel)) return false;
    return ((TerminalPanel)component).getSelection() != null;
  }

  public static @Nullable String getSelectedTextInTerminal(@Nullable Component component) {
    if (!(component instanceof TerminalPanel)) return null;
    return JBTerminalWidget.getSelectedText((TerminalPanel)component);
  }

  public static @Nullable String getTextInTerminal(@Nullable Component component) {
    if (!(component instanceof TerminalPanel)) return null;
    return JBTerminalWidget.getText((TerminalPanel)component);
  }
}
