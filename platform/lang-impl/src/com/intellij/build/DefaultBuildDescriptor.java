// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build;

import com.intellij.build.events.BuildEventsNls;
import com.intellij.build.process.BuildProcessHandler;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.util.Consumer;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * @author Vladislav.Soroka
 */
public class DefaultBuildDescriptor implements BuildDescriptor {

  private final Object myId;

  private final Object myGroupId;
  private final @BuildEventsNls.Title String myTitle;
  private final String myWorkingDir;
  private final long myStartTime;

  private boolean myActivateToolWindowWhenAdded = false;
  private boolean myActivateToolWindowWhenFailed = true;
  private @NotNull ThreeState myNavigateToError = ThreeState.UNSURE;
  private boolean myAutoFocusContent = false;

  private final @NotNull List<AnAction> myActions = new SmartList<>();
  private final @NotNull List<AnAction> myRestartActions = new SmartList<>();
  private final @NotNull List<Filter> myExecutionFilters = new SmartList<>();
  private final @NotNull List<Function<? super ExecutionNode, ? extends AnAction>> myContextActions = new SmartList<>();

  private @Nullable BuildProcessHandler myProcessHandler;
  private Consumer<? super ConsoleView> myAttachedConsoleConsumer;
  private @Nullable ExecutionEnvironment myExecutionEnvironment;
  private Supplier<? extends RunContentDescriptor> myContentDescriptorSupplier;

  public DefaultBuildDescriptor(@NotNull Object id,
                                @NotNull @BuildEventsNls.Title String title,
                                @NotNull String workingDir,
                                long startTime) {
    this(id, null, title, workingDir, startTime);
  }

  public DefaultBuildDescriptor(@NotNull Object id,
                                @Nullable Object groupId,
                                @NotNull @BuildEventsNls.Title String title,
                                @NotNull String workingDir,
                                long startTime) {
    myId = id;
    myGroupId = groupId;
    myTitle = title;
    myWorkingDir = workingDir;
    myStartTime = startTime;
  }

  public DefaultBuildDescriptor(@NotNull BuildDescriptor descriptor) {
    this(descriptor.getId(), descriptor.getGroupId(), descriptor.getTitle(), descriptor.getWorkingDir(), descriptor.getStartTime());
    if (descriptor instanceof DefaultBuildDescriptor defaultBuildDescriptor) {
      myActivateToolWindowWhenAdded = defaultBuildDescriptor.myActivateToolWindowWhenAdded;
      myActivateToolWindowWhenFailed = defaultBuildDescriptor.myActivateToolWindowWhenFailed;
      myAutoFocusContent = defaultBuildDescriptor.myAutoFocusContent;
      myNavigateToError = defaultBuildDescriptor.myNavigateToError;

      defaultBuildDescriptor.myRestartActions.forEach(this::withRestartAction);
      defaultBuildDescriptor.myActions.forEach(this::withAction);
      defaultBuildDescriptor.myExecutionFilters.forEach(this::withExecutionFilter);
      defaultBuildDescriptor.myContextActions.forEach(this::withContextAction);

      myContentDescriptorSupplier = defaultBuildDescriptor.myContentDescriptorSupplier;
      myExecutionEnvironment = defaultBuildDescriptor.myExecutionEnvironment;
      myProcessHandler = defaultBuildDescriptor.myProcessHandler;
      myAttachedConsoleConsumer = defaultBuildDescriptor.myAttachedConsoleConsumer;
    }
  }

  @Override
  public @NotNull Object getId() {
    return myId;
  }

  @Override
  public Object getGroupId() {
    return myGroupId;
  }

  @Override
  public @NotNull String getTitle() {
    return myTitle;
  }

  @Override
  public @NotNull String getWorkingDir() {
    return myWorkingDir;
  }

  @Override
  public long getStartTime() {
    return myStartTime;
  }

  @ApiStatus.Experimental
  public @NotNull List<AnAction> getActions() {
    return Collections.unmodifiableList(myActions);
  }

  @ApiStatus.Experimental
  public @NotNull List<AnAction> getRestartActions() {
    return Collections.unmodifiableList(myRestartActions);
  }

  @ApiStatus.Experimental
  public @NotNull List<AnAction> getContextActions(@NotNull ExecutionNode node) {
    return ContainerUtil.map(myContextActions, function -> function.apply(node));
  }

  @ApiStatus.Experimental
  public @NotNull List<Filter> getExecutionFilters() {
    return Collections.unmodifiableList(myExecutionFilters);
  }

  public boolean isActivateToolWindowWhenAdded() {
    return myActivateToolWindowWhenAdded;
  }

  public void setActivateToolWindowWhenAdded(boolean activateToolWindowWhenAdded) {
    myActivateToolWindowWhenAdded = activateToolWindowWhenAdded;
  }

  public boolean isActivateToolWindowWhenFailed() {
    return myActivateToolWindowWhenFailed;
  }

  public void setActivateToolWindowWhenFailed(boolean activateToolWindowWhenFailed) {
    myActivateToolWindowWhenFailed = activateToolWindowWhenFailed;
  }

  /**
   * If result is {@link ThreeState#YES} then IDEA has to navigate to error in file if that exists;
   * <p>If result is {@link ThreeState#NO} then IDEA must not navigate to errors in any case.
   * <p>If result is {@link ThreeState#UNSURE} then
   * it means that this value should be got from {@link BuildWorkspaceConfiguration#isShowFirstErrorInEditor()};
   */
  public @NotNull ThreeState isNavigateToError() {
    return myNavigateToError;
  }

  public void setNavigateToError(@NotNull ThreeState navigateToError) {
    myNavigateToError = navigateToError;
  }

  public boolean isAutoFocusContent() {
    return myAutoFocusContent;
  }

  public void setAutoFocusContent(boolean autoFocusContent) {
    myAutoFocusContent = autoFocusContent;
  }

  public @Nullable BuildProcessHandler getProcessHandler() {
    return myProcessHandler;
  }

  public @Nullable ExecutionEnvironment getExecutionEnvironment() {
    return myExecutionEnvironment;
  }

  public @Nullable Supplier<? extends RunContentDescriptor> getContentDescriptorSupplier() {
    return myContentDescriptorSupplier;
  }

  public Consumer<? super ConsoleView> getAttachedConsoleConsumer() {
    return myAttachedConsoleConsumer;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withAction(@NotNull AnAction action) {
    myActions.add(action);
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withActions(AnAction @NotNull ... actions) {
    myActions.addAll(Arrays.asList(actions));
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withRestartAction(@NotNull AnAction action) {
    myRestartActions.add(action);
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withRestartActions(AnAction @NotNull ... actions) {
    myRestartActions.addAll(Arrays.asList(actions));
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withContextAction(Function<? super ExecutionNode, ? extends AnAction> contextAction) {
    myContextActions.add(contextAction);
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withContextActions(AnAction @NotNull ... actions) {
    for (AnAction action : actions) {
      myContextActions.add(node -> action);
    }
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withExecutionFilter(@NotNull Filter filter) {
    myExecutionFilters.add(filter);
    return this;
  }

  @ApiStatus.Experimental
  public DefaultBuildDescriptor withExecutionFilters(Filter @NotNull ... filters) {
    myExecutionFilters.addAll(Arrays.asList(filters));
    return this;
  }

  public DefaultBuildDescriptor withContentDescriptor(Supplier<? extends RunContentDescriptor> contentDescriptorSupplier) {
    myContentDescriptorSupplier = contentDescriptorSupplier;
    return this;
  }

  public DefaultBuildDescriptor withProcessHandler(@Nullable BuildProcessHandler processHandler,
                                                   @Nullable Consumer<? super ConsoleView> attachedConsoleConsumer) {
    myProcessHandler = processHandler;
    myAttachedConsoleConsumer = attachedConsoleConsumer;
    return this;
  }

  public DefaultBuildDescriptor withExecutionEnvironment(@Nullable ExecutionEnvironment env) {
    myExecutionEnvironment = env;
    return this;
  }
}
