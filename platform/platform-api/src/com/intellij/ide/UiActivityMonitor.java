/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.util.BusyObject;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public abstract class UiActivityMonitor implements ApplicationComponent {

  public abstract BusyObject getBusy(@NotNull Project project, UiActivity ... toWatch);

  public abstract BusyObject getBusy(UiActivity ... toWatch);

  public abstract void addActivity(@NotNull Project project, @NotNull UiActivity activity);

  public abstract void addActivity(@NotNull Project project, @NotNull UiActivity activity, @NotNull ModalityState effectiveModalityState);

  public abstract void addActivity(@NotNull UiActivity activity);

  public abstract void addActivity(@NotNull UiActivity activity, @NotNull ModalityState effectiveModalityState);

  public abstract void removeActivity(@NotNull Project project, @NotNull UiActivity activity);

  public abstract void removeActivity(@NotNull UiActivity activity);

  public abstract void clear();

  public static UiActivityMonitor getInstance() {
    return ApplicationManager.getApplication().getComponent(UiActivityMonitor.class);
  }

}
