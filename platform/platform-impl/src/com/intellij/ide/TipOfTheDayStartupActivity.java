// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.wm.ToolWindowManager;
import org.jetbrains.annotations.NotNull;

public final class TipOfTheDayStartupActivity implements StartupActivity, DumbAware {
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
      TipsOfTheDayUsagesCollector.triggerShow("automatically");
      TipDialog.showForProject(project);
    }
  }
}
