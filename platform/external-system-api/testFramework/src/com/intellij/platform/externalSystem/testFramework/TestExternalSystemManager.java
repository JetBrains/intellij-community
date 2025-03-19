// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.testFramework;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.SimpleJavaParameters;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.ExternalSystemProjectResolver;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;

public class TestExternalSystemManager
  implements
  ExternalSystemManager<TestExternalProjectSettings,
    TestExternalSystemSettingsListener,
    TestExternalSystemSettings,
    TestExternalSystemLocalSettings,
    TestExternalSystemExecutionSettings>
{
  public TestExternalSystemManager(@NotNull Project project) {
    Disposer.register(project, systemSettings = new TestExternalSystemSettings(project));
    localSettings = new TestExternalSystemLocalSettings(project);
    executionSettings = new TestExternalSystemExecutionSettings();
  }

  @Override
  public @NotNull ProjectSystemId getSystemId() {
    return ExternalSystemTestUtil.TEST_EXTERNAL_SYSTEM_ID;
  }

  @Override
  public @NotNull Function<Project, TestExternalSystemSettings> getSettingsProvider() {
    return project -> getSystemSettings();
  }

  @Override
  public @NotNull Function<Project, TestExternalSystemLocalSettings> getLocalSettingsProvider() {
    return project -> getLocalSettings();
  }

  @Override
  public @NotNull Function<Pair<Project, String>, TestExternalSystemExecutionSettings> getExecutionSettingsProvider() {
    return pair -> getExecutionSettings();
  }

  @Override
  public @NotNull Class<? extends ExternalSystemProjectResolver<TestExternalSystemExecutionSettings>> getProjectResolverClass() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull Class<? extends ExternalSystemTaskManager<TestExternalSystemExecutionSettings>> getTaskManagerClass() {
    throw new UnsupportedOperationException();
  }

  @Override
  public @NotNull FileChooserDescriptor getExternalProjectDescriptor() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void enhanceRemoteProcessing(@NotNull SimpleJavaParameters parameters) throws ExecutionException {
  }

  public TestExternalSystemSettings getSystemSettings() {
    return systemSettings;
  }

  public void setSystemSettings(TestExternalSystemSettings systemSettings) {
    this.systemSettings = systemSettings;
  }

  public TestExternalSystemLocalSettings getLocalSettings() {
    return localSettings;
  }

  public void setLocalSettings(TestExternalSystemLocalSettings localSettings) {
    this.localSettings = localSettings;
  }

  public TestExternalSystemExecutionSettings getExecutionSettings() {
    return executionSettings;
  }

  public void setExecutionSettings(TestExternalSystemExecutionSettings executionSettings) {
    this.executionSettings = executionSettings;
  }

  private TestExternalSystemSettings systemSettings;
  private TestExternalSystemLocalSettings localSettings;
  private TestExternalSystemExecutionSettings executionSettings;
}
