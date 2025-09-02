// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class StackFramePopup {
  public static void show(@NotNull List<StackFrameItem> stack, DebugProcessImpl debugProcess) {
    // By default, show in the focus center and select the second element (usually it's a constructor call site).
    show(stack, debugProcess, null, 1);
  }

  public static void show(@NotNull List<StackFrameItem> stack, DebugProcessImpl debugProcess, @Nullable RelativePoint point, int selectedIndex) {
    StackFrameList list = new StackFrameList(debugProcess);
    list.setFrameItems(stack, () -> DebuggerUIUtil.invokeLater(() -> {
      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle(JavaDebuggerBundle.message("select.stack.frame"))
        .setAutoSelectIfEmpty(true)
        .setResizable(false)
        .setItemChosenCallback(() -> list.navigateToSelectedValue(true))
        .createPopup();

      list.setSelectedIndex(selectedIndex);
      if (point != null) {
        popup.show(point);
      }
      else {
        popup.showInFocusCenter();
      }
    }));
  }
}
