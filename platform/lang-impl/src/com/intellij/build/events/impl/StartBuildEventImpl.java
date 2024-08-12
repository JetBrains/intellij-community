// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build.events.impl;

import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * @author Vladislav.Soroka
 */
public final class StartBuildEventImpl extends StartEventImpl implements StartBuildEvent {

  private final @NotNull DefaultBuildDescriptor myBuildDescriptor;
  private @Nullable BuildViewSettingsProvider myBuildViewSettingsProvider;

  public StartBuildEventImpl(@NotNull BuildDescriptor descriptor, @NotNull @BuildEventsNls.Message  String message) {
    super(descriptor.getId(), null, descriptor.getStartTime(), message);
    myBuildDescriptor =
      descriptor instanceof DefaultBuildDescriptor ? (DefaultBuildDescriptor)descriptor : new DefaultBuildDescriptor(descriptor);
  }

  @ApiStatus.Experimental
  @Override
  public @NotNull DefaultBuildDescriptor getBuildDescriptor() {
    return myBuildDescriptor;
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

  @ApiStatus.Experimental
  public @Nullable BuildViewSettingsProvider getBuildViewSettingsProvider() {
    return myBuildViewSettingsProvider;
  }

  @ApiStatus.Experimental
  public StartBuildEventImpl withBuildViewSettingsProvider(@Nullable BuildViewSettingsProvider viewSettingsProvider) {
    myBuildViewSettingsProvider = viewSettingsProvider;
    return this;
  }
}
