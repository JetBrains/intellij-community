// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard;

import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.UiDataProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.ui.components.panels.NonOpaquePanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class RunDashboardComponentWrapper extends NonOpaquePanel implements UiDataProvider {
  public static final Key<Integer> CONTENT_ID_KEY = Key.create("RunDashboardContentId");
  private final Project myProject;

  private Integer myContentId;

  public RunDashboardComponentWrapper(@NotNull Project project) {
    myProject = project;
  }

  public @Nullable Integer getContentId() {
    return myContentId;
  }

  public void setContentId(@Nullable Integer contentId) {
    myContentId = contentId;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public void uiDataSnapshot(@NotNull DataSink sink) {
    sink.set(PlatformDataKeys.TREE_EXPANDER_HIDE_ACTIONS_IF_NO_EXPANDER, true);
  }
}
