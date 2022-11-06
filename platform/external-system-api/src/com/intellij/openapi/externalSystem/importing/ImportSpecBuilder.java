// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.importing;

import com.intellij.ide.plugins.advertiser.PluginFeatureEnabler;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public class ImportSpecBuilder {

  @NotNull private final Project myProject;
  @NotNull private final ProjectSystemId myExternalSystemId;
  @NotNull private ProgressExecutionMode myProgressExecutionMode;
  @Nullable private ExternalProjectRefreshCallback myCallback;
  private boolean isPreviewMode;
  private boolean isReportRefreshError = true;
  private @NotNull ThreeState isNavigateToError = ThreeState.UNSURE;
  @Nullable private String myVmOptions;
  @Nullable private String myArguments;
  private boolean myCreateDirectoriesForEmptyContentRoots;
  @Nullable private ProjectResolverPolicy myProjectResolverPolicy;

  public ImportSpecBuilder(@NotNull Project project, @NotNull ProjectSystemId id) {
    myProject = project;
    myExternalSystemId = id;
    myProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC;
  }

  public ImportSpecBuilder(ImportSpec importSpec) {
    this(importSpec.getProject(), importSpec.getExternalSystemId());
    apply(importSpec);
  }

  public ImportSpecBuilder use(@NotNull ProgressExecutionMode executionMode) {
    myProgressExecutionMode = executionMode;
    return this;
  }

  /**
   * @deprecated see {@link ImportSpecBuilder#forceWhenUptodate(boolean)}
   */
  @Deprecated
  public ImportSpecBuilder forceWhenUptodate() {
    return forceWhenUptodate(true);
  }

  /**
   * @deprecated it does nothing from
   * 16.02.2017, 16:42, ebef09cdbbd6ace3c79d3e4fb63028bac2f15f75
   */
  @Deprecated
  public ImportSpecBuilder forceWhenUptodate(boolean force) {
    return this;
  }

  public ImportSpecBuilder callback(@Nullable ExternalProjectRefreshCallback callback) {
    myCallback = callback;
    return this;
  }

  public ImportSpecBuilder usePreviewMode() {
    isPreviewMode = true;
    return this;
  }

  public ImportSpecBuilder createDirectoriesForEmptyContentRoots() {
    myCreateDirectoriesForEmptyContentRoots = true;
    return this;
  }

  public ImportSpecBuilder dontReportRefreshErrors() {
    isReportRefreshError = false;
    return this;
  }

  public ImportSpecBuilder dontNavigateToError() {
    isNavigateToError = ThreeState.NO;
    return this;
  }

  public ImportSpecBuilder navigateToError() {
    isNavigateToError = ThreeState.YES;
    return this;
  }

  public ImportSpecBuilder withVmOptions(@Nullable String vmOptions) {
    myVmOptions = vmOptions;
    return this;
  }

  public ImportSpecBuilder withArguments(@Nullable String arguments) {
    myArguments = arguments;
    return this;
  }

  @ApiStatus.Experimental
  public ImportSpecBuilder projectResolverPolicy(@NotNull ProjectResolverPolicy projectResolverPolicy) {
    myProjectResolverPolicy = projectResolverPolicy;
    return this;
  }

  public ImportSpec build() {
    ImportSpecImpl mySpec = new ImportSpecImpl(myProject, myExternalSystemId);
    mySpec.setProgressExecutionMode(myProgressExecutionMode);
    mySpec.setCreateDirectoriesForEmptyContentRoots(myCreateDirectoriesForEmptyContentRoots);
    mySpec.setPreviewMode(isPreviewMode);
    mySpec.setReportRefreshError(isReportRefreshError);
    mySpec.setNavigateToError(isNavigateToError);
    mySpec.setArguments(myArguments);
    mySpec.setVmOptions(myVmOptions);
    mySpec.setProjectResolverPolicy(myProjectResolverPolicy);
    ExternalProjectRefreshCallback callback;
    if (myCallback != null) {
      callback = myCallback;
    }
    else if (myProjectResolverPolicy == null || !myProjectResolverPolicy.isPartialDataResolveAllowed()) {
      callback = new DefaultProjectRefreshCallback(mySpec);
    }
    else {
      callback = null;
    }
    mySpec.setCallback(callback);
    return mySpec;
  }

  private void apply(ImportSpec spec) {
    myProgressExecutionMode = spec.getProgressExecutionMode();
    myCreateDirectoriesForEmptyContentRoots = spec.shouldCreateDirectoriesForEmptyContentRoots();
    myCallback = spec.getCallback();
    isPreviewMode = spec.isPreviewMode();
    isReportRefreshError = spec.isReportRefreshError();
    myArguments = spec.getArguments();
    myVmOptions = spec.getVmOptions();
  }

  @ApiStatus.Internal
  public final static class DefaultProjectRefreshCallback implements ExternalProjectRefreshCallback {
    private final Project myProject;
    private final ProgressExecutionMode myExecutionMode;

    public DefaultProjectRefreshCallback(ImportSpec spec) {
      myProject = spec.getProject();
      myExecutionMode = spec.getProgressExecutionMode();
    }

    @Override
    public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
      if (externalProject == null) {
        return;
      }
      final boolean synchronous = myExecutionMode == ProgressExecutionMode.MODAL_SYNC;

      PluginFeatureEnabler.getInstance(myProject).scheduleEnableSuggested();

      ProjectDataManager.getInstance().importData(externalProject,
                                                  myProject
      );
    }
  }
}
