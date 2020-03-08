/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import org.jetbrains.annotations.NotNull;

public class MaintenanceAction extends AnAction implements DumbAware {
  public MaintenanceAction() {
    super(ActionsBundle.messagePointer("action.MaintenanceAction.text"));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final ActionGroup group = (ActionGroup)ActionManager.getInstance().getAction("MaintenanceGroup");
    JBPopupFactory.getInstance().
      createActionGroupPopup(IdeBundle.message("popup.title.maintenance"), group, e.getDataContext(), JBPopupFactory.ActionSelectionAid.NUMBERING, true).
      showInFocusCenter();
  }
}