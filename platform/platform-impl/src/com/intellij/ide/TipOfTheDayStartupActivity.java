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
package com.intellij.ide;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public class TipOfTheDayStartupActivity implements StartupActivity, DumbAware {
  private boolean myVeryFirstProjectOpening = true;

  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    if (!myVeryFirstProjectOpening || !GeneralSettings.getInstance().isShowTipsOnStartup()) {
      return;
    }

    myVeryFirstProjectOpening = false;
    runImpl(project, 3);
  }

  private static void runImpl(Project project, int delayCount) {
    if (project.isDisposed()) return;
    // cancel "tips on start-up" right before the show
    if (!GeneralSettings.getInstance().isShowTipsOnStartup()) return;
    if (delayCount > 0) {
      ToolWindowManager.getInstance(project).invokeLater(() -> runImpl(project, delayCount - 1));
    }
    else {
      TipsOfTheDayUsagesCollector.trigger("shown.automatically");
      TipDialog.showForProject(project);
    }
  }
}
