// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEventsNls.Description;
import com.intellij.build.events.BuildEventsNls.Hint;
import com.intellij.build.events.BuildEventsNls.Message;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Vladislav.Soroka
 */
@Internal
public final class StartBuildEventImpl extends StartEventImpl implements StartBuildEvent {

  private final @NotNull DefaultBuildDescriptor myBuildDescriptor;
  private @Nullable BuildViewSettingsProvider myBuildViewSettings;

  @Internal
  public StartBuildEventImpl(
    @Nullable Object parentId,
    @NotNull @Message String message,
    @Nullable @Hint String hint,
    @Nullable @Description String description,
    @NotNull BuildDescriptor buildDescriptor,
    @Nullable BuildViewSettingsProvider buildViewSettings
  ) {
    super(buildDescriptor.getId(), parentId, buildDescriptor.getStartTime(), message, hint, description);
    myBuildDescriptor = buildDescriptor instanceof DefaultBuildDescriptor defaultBuildDescriptor
                        ? defaultBuildDescriptor : new DefaultBuildDescriptor(buildDescriptor);
    myBuildViewSettings = buildViewSettings;
  }

  /**
   * @deprecated Use {@link StartBuildEvent#builder} event builder instead.
   */
  @Deprecated
  public StartBuildEventImpl(
    @NotNull BuildDescriptor descriptor,
    @NotNull @Message String message
  ) {
    this(null, message, null, null, descriptor, null);
  }

  @Override
  public @NotNull DefaultBuildDescriptor getBuildDescriptor() {
    return myBuildDescriptor;
  }

  @Override
  public @Nullable BuildViewSettingsProvider getBuildViewSettings() {
    return myBuildViewSettings;
  }

  /**
   * @deprecated use {@link DefaultBuildDescriptor#withProcessHandler}
   */
  @Deprecated(forRemoval = true)
  public StartBuildEventImpl withRestartActions(AnAction... actions) {
    Arrays.stream(actions).forEach(myBuildDescriptor::withRestartAction);
    return this;
  }

  /**
   * @deprecated use {@link DefaultBuildDescriptor#withProcessHandler}
   */
  @Deprecated(forRemoval = true)
  public StartBuildEventImpl withExecutionFilter(@NotNull Filter filter) {
    myBuildDescriptor.withExecutionFilter(filter);
    return this;
  }

  /**
   * @deprecated Use {@link #getBuildViewSettings()} instead.
   */
  @Deprecated
  @Experimental
  public @Nullable BuildViewSettingsProvider getBuildViewSettingsProvider() {
    return myBuildViewSettings;
  }

  /**
   * @deprecated Use {@link StartBuildEvent#builder} event builder instead.
   */
  @Deprecated
  @Experimental
  public StartBuildEventImpl withBuildViewSettingsProvider(@Nullable BuildViewSettingsProvider viewSettingsProvider) {
    myBuildViewSettings = viewSettingsProvider;
    return this;
  }
}
