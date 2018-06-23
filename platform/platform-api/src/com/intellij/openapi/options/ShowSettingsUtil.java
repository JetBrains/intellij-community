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
package com.intellij.openapi.options;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;

public abstract class ShowSettingsUtil {
  public static ShowSettingsUtil getInstance() {
    return ServiceManager.getService(ShowSettingsUtil.class);
  }

  public abstract void showSettingsDialog(Project project, ConfigurableGroup... group);

  public abstract void showSettingsDialog(@Nullable Project project, Class toSelect);

  public abstract void showSettingsDialog(@Nullable Project project, @NotNull String nameToSelect);

  public abstract void showSettingsDialog(@NotNull final Project project, final Configurable toSelect);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project,
                                                                   @NotNull Class<T> configurableClass,
                                                                   @Nullable Consumer<T> additionalConfiguration);
  
  public abstract boolean editConfigurable(Project project, Configurable configurable);

  public abstract boolean editConfigurable(@Nullable Project project, Configurable configurable, @Nullable Runnable advancedInitialization);

  public abstract boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(Component parent, Configurable configurable, Runnable advancedInitialization);

  public abstract boolean editConfigurable(Project project, @NonNls String dimensionServiceKey, Configurable configurable);

  public abstract boolean editConfigurable(Project project, @NonNls String dimensionServiceKey, Configurable configurable, boolean showApplyButton);

  public abstract boolean editConfigurable(Component parent, String dimensionServiceKey, Configurable configurable);

  /**
   * @deprecated create a new instance of configurable instead
   * to remove in IDEA 15
   */
  @Deprecated
  public abstract <T extends Configurable> T findProjectConfigurable(Project project, Class<T> confClass);

  /**
   * @deprecated create a new instance of configurable instead
   * to remove in IDEA 15
   */
  @Deprecated
  public abstract <T extends Configurable> T findApplicationConfigurable(Class<T> confClass);

  public static String getSettingsMenuName() {
    return SystemInfo.isMac ? "Preferences" : "Settings";
  }
}