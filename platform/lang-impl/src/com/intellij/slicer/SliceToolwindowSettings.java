/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.slicer;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

@State(
    name = "SliceToolwindowSettings",
    storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)}
)
public class SliceToolwindowSettings implements PersistentStateComponent<SliceToolwindowSettings> {
  private boolean isPreview;
  private boolean isAutoScroll;

  public static SliceToolwindowSettings getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, SliceToolwindowSettings.class);
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
  public void loadState(SliceToolwindowSettings state) {
    isAutoScroll = state.isAutoScroll();
    isPreview = state.isPreview();
  }
}
