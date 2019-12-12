// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ide.util.TipDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NotNull;

public final class TipOfTheDayStartupActivity implements StartupActivity.DumbAware {

  private Alarm myAlarm;

  @Override
  public void runActivity(@NotNull final Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode() || myAlarm != null) return;

    myAlarm = new Alarm(project);
    myAlarm.addRequest(() -> showTipsIfNeed(project), 5000, ModalityState.any());
  }

  private static void showTipsIfNeed(@NotNull Project project) {
    if (!project.isDisposed() && TipDialog.canBeShownAutomaticallyNow()) {
        TipsOfTheDayUsagesCollector.triggerShow("automatically");
        TipDialog.showForProject(project);
    }
  }
}
