// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public final class StackFramePopup {
  public static void show(@NotNull List<StackFrameItem> stack, DebugProcessImpl debugProcess) {
    StackFrameList list = new StackFrameList(debugProcess);
    list.setFrameItems(stack, () -> DebuggerUIUtil.invokeLater(() -> {
      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle(JavaDebuggerBundle.message("select.stack.frame"))
        .setAutoSelectIfEmpty(true)
        .setResizable(false)
        .setItemChoosenCallback(() -> list.navigateToSelectedValue(true))
        .createPopup();

      list.setSelectedIndex(1);
      popup.showInFocusCenter();
    }));
  }
}
