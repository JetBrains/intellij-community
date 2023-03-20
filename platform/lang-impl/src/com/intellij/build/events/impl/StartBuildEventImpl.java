/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.build.events.impl;

import com.intellij.build.BuildDescriptor;
import com.intellij.build.BuildViewSettingsProvider;
import com.intellij.build.DefaultBuildDescriptor;
import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public class StartBuildEventImpl extends StartEventImpl implements StartBuildEvent {

  private final @NotNull DefaultBuildDescriptor myBuildDescriptor;
  private @Nullable BuildViewSettingsProvider myBuildViewSettingsProvider;

  public StartBuildEventImpl(@NotNull BuildDescriptor descriptor, @NotNull @BuildEventsNls.Message  String message) {
    super(descriptor.getId(), null, descriptor.getStartTime(), message);
    myBuildDescriptor =
      descriptor instanceof DefaultBuildDescriptor ? (DefaultBuildDescriptor)descriptor : new DefaultBuildDescriptor(descriptor);
  }

  @ApiStatus.Experimental
  @NotNull
  @Override
  public DefaultBuildDescriptor getBuildDescriptor() {
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
  public StartBuildEventImpl withContentDescriptorSupplier(Supplier<? extends RunContentDescriptor> contentDescriptorSupplier) {
    myBuildDescriptor.withContentDescriptor(contentDescriptorSupplier);
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

  @Nullable
  @ApiStatus.Experimental
  public BuildViewSettingsProvider getBuildViewSettingsProvider() {
    return myBuildViewSettingsProvider;
  }

  @ApiStatus.Experimental
  public StartBuildEventImpl withBuildViewSettingsProvider(@Nullable BuildViewSettingsProvider viewSettingsProvider) {
    myBuildViewSettingsProvider = viewSettingsProvider;
    return this;
  }
}
