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
import com.intellij.build.events.StartBuildEvent;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public class StartBuildEventImpl extends StartEventImpl implements StartBuildEvent {

  private final String myBuildTitle;
  private final String myWorkingDir;
  @Nullable
  private ProcessHandler myProcessHandler;
  private Consumer<ConsoleView> myAttachedConsoleConsumer;
  @NotNull
  private List<AnAction> myRestartActions = new SmartList<>();
  @NotNull
  private List<Filter> myFilters = new SmartList<>();
  @Nullable
  private ExecutionEnvironment myExecutionEnvironment;
  @Nullable
  private Supplier<RunContentDescriptor> myContentDescriptorSupplier;

  public StartBuildEventImpl(@NotNull BuildDescriptor descriptor, @NotNull String message) {
    super(descriptor.getId(), null, descriptor.getStartTime(), message);
    myBuildTitle = descriptor.getTitle();
    myWorkingDir = descriptor.getWorkingDir();
  }

  @Override
  public String getBuildTitle() {
    return myBuildTitle;
  }

  @NotNull
  @Override
  public String getWorkingDir() {
    return myWorkingDir;
  }

  @Nullable
  @Override
  public ProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  @Nullable
  @Override
  public ExecutionEnvironment getExecutionEnvironment() {
    return myExecutionEnvironment;
  }

  @NotNull
  @Override
  public AnAction[] getRestartActions() {
    return myRestartActions.toArray(new AnAction[myRestartActions.size()]);
  }

  @NotNull
  @Override
  public Filter[] getExecutionFilters() {
    return myFilters.toArray(new Filter[myFilters.size()]);
  }

  @Nullable
  @Override
  public Supplier<RunContentDescriptor> getContentDescriptorSupplier() {
    return myContentDescriptorSupplier;
  }

  @Nullable
  @Override
  public Consumer<ConsoleView> getAttachedConsoleConsumer() {
    return myAttachedConsoleConsumer;
  }

  public StartBuildEventImpl withProcessHandler(@Nullable ProcessHandler processHandler,
                                                @Nullable Consumer<ConsoleView> attachedConsoleConsumer) {
    myProcessHandler = processHandler;
    myAttachedConsoleConsumer = attachedConsoleConsumer;
    return this;
  }

  public StartBuildEventImpl withRestartAction(@NotNull AnAction anAction) {
    myRestartActions.add(anAction);
    return this;
  }

  public StartBuildEventImpl withRestartActions(AnAction... actions) {
    myRestartActions.addAll(Arrays.asList(actions));
    return this;
  }

  public StartBuildEventImpl withExecutionEnvironment(ExecutionEnvironment env) {
    myExecutionEnvironment = env;
    return this;
  }

  public StartBuildEventImpl withContentDescriptorSupplier(Supplier<RunContentDescriptor> contentDescriptorSupplier) {
    myContentDescriptorSupplier = contentDescriptorSupplier;
    return this;
  }

  public StartBuildEventImpl withExecutionFilter(@NotNull Filter filter) {
    myFilters.add(filter);
    return this;
  }

  public StartBuildEventImpl withExecutionFilters(Filter... filters) {
    myFilters.addAll(Arrays.asList(filters));
    return this;
  }
}
