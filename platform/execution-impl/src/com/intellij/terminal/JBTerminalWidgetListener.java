// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.terminal;

public interface JBTerminalWidgetListener {
  void onNewSession();

  void onTerminalStarted();

  void onPreviousTabSelected();

  void onNextTabSelected();

  void onSessionClosed();

  void showTabs();

  default void moveTabRight() {}

  default void moveTabLeft() {}

  default boolean canMoveTabRight() {return false;}

  default boolean canMoveTabLeft() {return false;}

  default boolean canSplit(boolean vertically) {
    return false;
  }

  default void split(boolean vertically) {}

  default boolean isGotoNextSplitTerminalAvailable() {
    return false;
  }

  default void gotoNextSplitTerminal(boolean forward) {}
}
