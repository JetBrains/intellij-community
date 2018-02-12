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

package com.intellij.execution.actions;

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindowId;

public class ChooseRunConfigurationPopupAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    assert project != null;

    new ChooseRunConfigurationPopup(project,
                                    getAdKey(),
                                    getDefaultExecutor(),
                                    getAlternativeExecutor()).show();
  }

  protected Executor getDefaultExecutor() {
    return DefaultRunExecutor.getRunExecutorInstance();
  }

  protected Executor getAlternativeExecutor() {
    return ExecutorRegistry.getInstance().getExecutorById(ToolWindowId.DEBUG);
  }

  protected String getAdKey() {
    return "run.configuration.alternate.action.ad";
  }

  @Override
  public void update(AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final Project project = e.getData(CommonDataKeys.PROJECT);

    presentation.setEnabled(true);
    if (project == null || project.isDisposed()) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    if (null == getDefaultExecutor()) {
      presentation.setEnabled(false);
      presentation.setVisible(false);
      return;
    }

    presentation.setEnabled(true);
    presentation.setVisible(true);
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }
}
