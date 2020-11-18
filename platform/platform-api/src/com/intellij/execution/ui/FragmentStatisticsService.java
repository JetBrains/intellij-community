// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;

public abstract class FragmentStatisticsService {

  public static FragmentStatisticsService getInstance() {
    return ApplicationManager.getApplication().getService(FragmentStatisticsService.class);
  }

  public abstract void logOptionModified(Project project, String optionId, String runConfigId, AnActionEvent inputEvent);
  public abstract void logOptionRemoved(Project project, String optionId, String runConfigId, AnActionEvent inputEvent);
}
