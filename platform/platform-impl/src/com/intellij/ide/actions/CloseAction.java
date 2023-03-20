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
package com.intellij.ide.actions;

import com.intellij.icons.AllIcons;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class CloseAction extends AnAction implements DumbAware, LightEditCompatible {

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setIcon(e.isFromActionToolbar() ? AllIcons.Actions.Cancel : null);

    CloseTarget closeTarget = e.getData(CloseTarget.KEY);
    e.getPresentation().setEnabled(closeTarget != null);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    CloseTarget closeTarget = e.getData(CloseTarget.KEY);
    if (closeTarget == null) return;
    closeTarget.close();
  }

  public interface CloseTarget {
    DataKey<CloseTarget> KEY = DataKey.create("GenericClosable");

    void close();
  }
}
