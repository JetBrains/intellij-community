// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

public abstract class ShowSettingsUtil {
  public static ShowSettingsUtil getInstance() {
    return ServiceManager.getService(ShowSettingsUtil.class);
  }

  public abstract void showSettingsDialog(Project project, ConfigurableGroup... group);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project, @NotNull Class<T> toSelect);

  public abstract void showSettingsDialog(@Nullable Project project, @NotNull String nameToSelect);

  public abstract void showSettingsDialog(@NotNull Project project, @Nullable Configurable toSelect);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project,
                                                                   @NotNull Class<T> configurableClass,
                                                                   @Nullable Consumer<? super T> additionalConfiguration);

  public abstract void showSettingsDialog(@Nullable Project project,
                                          @NotNull Predicate<? super Configurable> predicate,
                                          @Nullable Consumer<? super Configurable> additionalConfiguration);

  public abstract boolean editConfigurable(Project project, Configurable configurable);

  public abstract boolean editConfigurable(@Nullable Project project, Configurable configurable, @Nullable Runnable advancedInitialization);

  public abstract boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(Component parent, Configurable configurable, Runnable advancedInitialization);

  public abstract boolean editConfigurable(Project project, @NonNls String dimensionServiceKey, Configurable configurable);

  public abstract boolean editConfigurable(Project project, @NonNls String dimensionServiceKey, Configurable configurable, boolean showApplyButton);

  public abstract boolean editConfigurable(Component parent, String dimensionServiceKey, Configurable configurable);

  public static String getSettingsMenuName() {
    return SystemInfo.isMac ? "Preferences" : "Settings";
  }
}