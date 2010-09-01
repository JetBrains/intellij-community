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
package com.intellij.openapi.options;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public abstract class ShowSettingsUtil {
  public static ShowSettingsUtil getInstance() {
    return ServiceManager.getService(ShowSettingsUtil.class);
  }

  public abstract void showSettingsDialog(Project project, ConfigurableGroup[] group);

  public abstract void showSettingsDialog(@Nullable Project project, Class toSelect);

  public abstract void showSettingsDialog(@Nullable Project project, @NotNull String nameToSelect);

  public abstract void showSettingsDialog(@NotNull final Project project, final Configurable toSelect);

  public abstract boolean editConfigurable(Project project, Configurable configurable);

  public abstract boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization);

  public abstract boolean editConfigurable(Component parent, Configurable configurable);

  public abstract boolean editConfigurable(Component parent, Configurable configurable, Runnable advancedInitialization);

  public abstract boolean editConfigurable(Project project, @NonNls String dimensionServiceKey, Configurable configurable);

  @NotNull
  public abstract <T extends Configurable> T createProjectConfigurable(@NotNull Project project, Class<T> configurableClass);

  @NotNull
  public abstract <T extends Configurable> T createApplicationConfigurable(Class<T> configurableClass);

  public abstract boolean editConfigurable(Component parent, String dimensionServiceKey, Configurable configurable);

  /**
   * @deprecated use {@link #createProjectConfigurable} instead
   */
  public abstract <T extends Configurable> T findProjectConfigurable(Project project, Class<T> confClass);

  /**
   * @deprecated use {@link #createApplicationConfigurable} instead
   */
  public abstract <T extends Configurable> T findApplicationConfigurable(Class<T> confClass);
}