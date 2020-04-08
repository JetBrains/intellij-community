// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Opens Settings dialog or specific {@link Configurable}.
 */
public abstract class ShowSettingsUtil {
  public static ShowSettingsUtil getInstance() {
    return ServiceManager.getService(ShowSettingsUtil.class);
  }

  public abstract void showSettingsDialog(@NotNull Project project, ConfigurableGroup @NotNull ... groups);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project, @NotNull Class<T> toSelect);

  public abstract void showSettingsDialog(@Nullable Project project, @NotNull String nameToSelect);

  public abstract void showSettingsDialog(@NotNull Project project, @Nullable Configurable toSelect);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project,
                                                                   @NotNull Class<T> configurableClass,
                                                                   @Nullable Consumer<? super T> additionalConfiguration);

  public abstract void showSettingsDialog(@Nullable Project project,
                                          @NotNull Predicate<? super Configurable> predicate,
                                          @Nullable Consumer<? super Configurable> additionalConfiguration);

  public abstract boolean editConfigurable(Project project, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(@Nullable Project project, @NotNull Configurable configurable, @Nullable Runnable advancedInitialization);

  public abstract boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(@Nullable Component parent, @NotNull String displayName);

  public abstract boolean editConfigurable(@Nullable Component parent, @NotNull String displayName, @Nullable Runnable advancedInitialization);

  public abstract boolean editConfigurable(Component parent, @NotNull Configurable configurable, Runnable advancedInitialization);

  public abstract boolean editConfigurable(Project project, @NonNls @NotNull String dimensionServiceKey, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(Project project, @NonNls @NotNull String dimensionServiceKey, @NotNull Configurable configurable, boolean showApplyButton);

  public abstract boolean editConfigurable(Component parent, @NonNls @NotNull String dimensionServiceKey, @NotNull Configurable configurable);

  /**
   * OS-specific name.
   */
  public static String getSettingsMenuName() {
    return SystemInfo.isMac ? "Preferences" : "Settings";
  }
}