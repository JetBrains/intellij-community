/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.intellij.execution.ui.layout.actions;

import com.intellij.execution.ui.layout.impl.RunnerContentUi;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ToggleToolbarLayoutAction extends ToggleAction {
  @Override
  public void update(final AnActionEvent e) {
    if (getRunnerUi(e) == null) {
      e.getPresentation().setEnabled(false);
    }
    else {
      super.update(e);
    }
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    RunnerContentUi ui = getRunnerUi(e);
    return ui != null && ui.isHorizontalToolbar();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    RunnerContentUi ui = getRunnerUi(e);
    assert ui != null;
    ui.setHorizontalToolbar(state);
  }

  @Nullable
  public static RunnerContentUi getRunnerUi(@NotNull AnActionEvent e) {
    return RunnerContentUi.KEY.getData(e.getDataContext());
  }
}
