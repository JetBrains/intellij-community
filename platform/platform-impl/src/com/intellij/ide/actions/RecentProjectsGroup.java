/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RecentProjectsGroup extends ActionGroup implements DumbAware {
  public RecentProjectsGroup() {
    super();

    final Presentation templatePresentation = getTemplatePresentation();
    // Let's make tile more macish
    if (SystemInfo.isMac) {
      templatePresentation.setText(ActionsBundle.message("group.reopen.mac.text"));
    } else {
      templatePresentation.setText(ActionsBundle.message("group.reopen.win.text"));
    }
  }

  @NotNull
  public AnAction[] getChildren(@Nullable AnActionEvent e) {
    return RecentProjectsManagerBase.getInstance().getRecentProjectsActions(true);
  }

  public void update(AnActionEvent event) {
    Presentation presentation = event.getPresentation();
    presentation.setEnabled(RecentProjectsManagerBase.getInstance().getRecentProjectsActions(true).length > 0);
  }
}
