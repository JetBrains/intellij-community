// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nls;
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
    return ApplicationManager.getApplication().getService(ShowSettingsUtil.class);
  }

  public abstract void showSettingsDialog(@NotNull Project project, ConfigurableGroup @NotNull ... groups);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project, @NotNull Class<T> toSelect);

  public abstract void showSettingsDialog(@Nullable Project project, @NlsContexts.ConfigurableName @NotNull String nameToSelect);

  public abstract void showSettingsDialog(@NotNull Project project, @Nullable Configurable toSelect);

  public abstract <T extends Configurable> void showSettingsDialog(@Nullable Project project,
                                                                   @NotNull Class<T> configurableClass,
                                                                   @Nullable Consumer<? super T> additionalConfiguration);

  public abstract void showSettingsDialog(@Nullable Project project,
                                          @NotNull Predicate<? super Configurable> predicate,
                                          @Nullable Consumer<? super Configurable> additionalConfiguration);

  /**
   * Show a dialog with a defined configurable.
   * <p>
   * editConfigurable method is a good choice to create and show a quick on-call created configurable.
   * If you'd like to show a configurable that is a part of the settings dialog, prefer using showSettingsDialog method
   */
  public abstract boolean editConfigurable(Project project, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(@Nullable Project project, @NotNull Configurable configurable, @Nullable Runnable advancedInitialization);

  public abstract <T extends Configurable> boolean editConfigurable(@Nullable Project project, @NotNull T configurable, @NotNull Consumer<? super T> advancedInitialization);

  public abstract boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(@Nullable Component parent, @NlsContexts.ConfigurableName @NotNull String displayName);

  public abstract boolean editConfigurable(@Nullable Component parent, @NlsContexts.ConfigurableName @NotNull String displayName, @Nullable Runnable advancedInitialization);

  public abstract boolean editConfigurable(Component parent, @NotNull Configurable configurable, Runnable advancedInitialization);

  public abstract boolean editConfigurable(Project project, @NonNls @NotNull String dimensionServiceKey, @NotNull Configurable configurable);

  public abstract boolean editConfigurable(Project project, @NonNls @NotNull String dimensionServiceKey, @NotNull Configurable configurable, boolean showApplyButton);

  public abstract boolean editConfigurable(Component parent, @NonNls @NotNull String dimensionServiceKey, @NotNull Configurable configurable);

  /**
   * OS-specific name.
   */
  public static @Nls String getSettingsMenuName() {
    return CommonBundle.settingsTitle();
  }
}