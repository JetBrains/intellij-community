/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.debugger.memory.ui;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StackFramePopup {
  public static void show(@NotNull List<StackFrameItem> stack, DebugProcessImpl debugProcess) {
    StackFrameList list = new StackFrameList(debugProcess);
    list.setFrameItems(stack, () -> DebuggerUIUtil.invokeLater(() -> {
      JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Select stack frame")
        .setAutoSelectIfEmpty(true)
        .setResizable(false)
        .setItemChoosenCallback(() -> list.navigateToSelectedValue(true))
        .createPopup();

      list.setSelectedIndex(1);
      popup.showInFocusCenter();
    }));
  }
}
