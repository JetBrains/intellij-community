// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.slicer;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
@State(name = "SliceToolwindowSettings", storages = @Storage(StoragePathMacros.WORKSPACE_FILE))
public final class SliceToolwindowSettings implements PersistentStateComponent<SliceToolwindowSettings> {
  private boolean isPreview;
  private boolean isAutoScroll;

  public static SliceToolwindowSettings getInstance(@NotNull Project project) {
    return project.getService(SliceToolwindowSettings.class);
  }
  public boolean isPreview() {
    return isPreview;
  }

  public void setPreview(boolean preview) {
    isPreview = preview;
  }

  public boolean isAutoScroll() {
    return isAutoScroll;
  }

  public void setAutoScroll(boolean autoScroll) {
    isAutoScroll = autoScroll;
  }

  @Override
  public SliceToolwindowSettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull SliceToolwindowSettings state) {
    isAutoScroll = state.isAutoScroll();
    isPreview = state.isPreview();
  }
}
