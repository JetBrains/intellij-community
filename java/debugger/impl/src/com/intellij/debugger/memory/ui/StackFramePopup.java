/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import com.intellij.debugger.memory.utils.StackFrameItem;

import java.util.List;

public class StackFramePopup {
  private final Project myProject;
  private final List<StackFrameItem> myStackFrame;
  private final GlobalSearchScope myScope;

  public StackFramePopup(@NotNull Project project,
                         @NotNull List<StackFrameItem> stack,
                         @NotNull GlobalSearchScope searchScope) {
    myProject = project;
    myStackFrame = stack;
    myScope = searchScope;
  }

  public void show() {
    StackFrameList list = new StackFrameList(myProject, myStackFrame, myScope);
    list.addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        list.navigateToSelectedValue(false);
      }
    });

    JBPopup popup = JBPopupFactory.getInstance().createListPopupBuilder(list)
        .setTitle("Select stack frame")
        .setAutoSelectIfEmpty(true)
        .setResizable(false)
        .setItemChoosenCallback(() -> list.navigateToSelectedValue(true))
        .createPopup();

    list.setSelectedIndex(1);
    popup.showInFocusCenter();
  }
}
