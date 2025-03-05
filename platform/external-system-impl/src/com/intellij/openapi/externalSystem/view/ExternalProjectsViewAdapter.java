// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.view;

import com.intellij.openapi.externalSystem.ExternalSystemUiAware;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemShortcutsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public class ExternalProjectsViewAdapter implements ExternalProjectsView {
  private final @NotNull ExternalProjectsView delegate;

  public ExternalProjectsViewAdapter(@NotNull ExternalProjectsView delegate) {
    this.delegate = delegate;
  }

  @Override
  public ExternalSystemUiAware getUiAware() {
    return delegate.getUiAware();
  }

  @Override
  public @Nullable ExternalProjectsStructure getStructure() {
    return delegate.getStructure();
  }

  @ApiStatus.Internal
  @Override
  public ExternalSystemShortcutsManager getShortcutsManager() {
    return delegate.getShortcutsManager();
  }

  @Override
  public ExternalSystemTaskActivator getTaskActivator() {
    return delegate.getTaskActivator();
  }

  @Override
  public void updateUpTo(ExternalSystemNode node) {
    delegate.updateUpTo(node);
  }

  @Override
  public List<ExternalSystemNode<?>> createNodes(@NotNull ExternalProjectsView externalProjectsView,
                                                 @Nullable ExternalSystemNode<?> parent,
                                                 @NotNull DataNode<?> dataNode) {
    return delegate.createNodes(externalProjectsView, parent, dataNode);
  }

  @Override
  public ExternalProjectsStructure.ErrorLevel getErrorLevelRecursively(@NotNull DataNode node) {
    return delegate.getErrorLevelRecursively(node);
  }

  @Override
  public Project getProject() {
    return delegate.getProject();
  }

  @Override
  public boolean showInheritedTasks() {
    return delegate.showInheritedTasks();
  }

  @Override
  public boolean getGroupTasks() {
    return delegate.getGroupTasks();
  }

  @Override
  public boolean getGroupModules() {
    return delegate.getGroupModules();
  }

  @Override
  public boolean useTasksNode() {
    return delegate.useTasksNode();
  }

  @Override
  public ProjectSystemId getSystemId() {
    return delegate.getSystemId();
  }

  @Override
  public void handleDoubleClickOrEnter(@NotNull ExternalSystemNode node, @Nullable String actionId, InputEvent inputEvent) {
    delegate.handleDoubleClickOrEnter(node, actionId, inputEvent);
  }

  @Override
  public void addListener(@NotNull Listener listener) {
    delegate.addListener(listener);
  }

  @Override
  public boolean getShowIgnored() {
    return delegate.getShowIgnored();
  }

  @Override
  public String getDisplayName(DataNode node) {
    return delegate.getDisplayName(node);
  }
}
