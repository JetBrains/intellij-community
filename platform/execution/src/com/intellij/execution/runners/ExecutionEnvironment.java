// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.*;
import com.intellij.execution.target.*;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.ide.ui.IdeUiService;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.CustomizedDataContext;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates all the settings, information, target, and program runner required to execute a process.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/execution.html">Execution (IntelliJ Platform Docs)</a>
 */
public final class ExecutionEnvironment extends UserDataHolderBase implements Disposable {
  private static final AtomicLong myIdHolder = new AtomicLong(1L);

  private final @NotNull Project myProject;

  private final @NotNull RunProfile myRunProfile;
  private final @NotNull Executor myExecutor;

  private final @NotNull ExecutionTarget myTarget;
  private TargetEnvironmentRequest myTargetEnvironmentRequest;
  private volatile TargetEnvironment myPrepareRemoteEnvironment;

  private @Nullable RunnerSettings myRunnerSettings;
  private @Nullable ConfigurationPerRunnerSettings myConfigurationSettings;
  private final @Nullable RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  private @Nullable RunContentDescriptor myContentToReuse;
  private final ProgramRunner<?> myRunner;
  private long myExecutionId = 0;
  private @Nullable DataContext myDataContext;
  private @Nullable String myModulePath;

  private @Nullable ProgramRunner.Callback callback;
  private boolean isHeadless = false;

  /**
   * {@code true} means that a special `Current File` item was selected in the Run Configurations combo box,
   * so the run configuration was auto-created for the file, which was opened in the editor. (See IJP-1167)
   */
  private boolean myRunningCurrentFile;

  @TestOnly
  public ExecutionEnvironment() {
    myProject = null;
    myContentToReuse = null;
    myRunnerAndConfigurationSettings = null;
    myExecutor = null;
    myRunner = null;
    myRunProfile = null;
    myTarget = null;
  }

  public ExecutionEnvironment(@NotNull Executor executor,
                              @NotNull ProgramRunner runner,
                              @NotNull RunnerAndConfigurationSettings settings,
                              @NotNull Project project) {
    this(settings.getConfiguration(),
         executor,
         DefaultExecutionTarget.INSTANCE,
         project,
         settings.getRunnerSettings(runner),
         settings.getConfigurationSettings(runner),
         null,
         settings,
         runner, null);
  }

  ExecutionEnvironment(@NotNull RunProfile runProfile,
                       @NotNull Executor executor,
                       @NotNull ExecutionTarget target,
                       @NotNull Project project,
                       @Nullable RunnerSettings runnerSettings,
                       @Nullable ConfigurationPerRunnerSettings configurationSettings,
                       @Nullable RunContentDescriptor contentToReuse,
                       @Nullable RunnerAndConfigurationSettings settings,
                       @NotNull ProgramRunner<?> runner,
                       @Nullable ProgramRunner.Callback callback) {
    myExecutor = executor;
    myTarget = target;
    myRunProfile = runProfile;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    myProject = project;
    setContentToReuse(contentToReuse);
    myRunnerAndConfigurationSettings = settings;

    myRunner = runner;

    this.callback = callback;
  }

  public @NotNull TargetEnvironmentRequest getTargetEnvironmentRequest() {
    if (myTargetEnvironmentRequest != null) {
      return myTargetEnvironmentRequest;
    }
    return myTargetEnvironmentRequest = createTargetEnvironmentRequest();
  }

  private @NotNull TargetEnvironmentRequest createTargetEnvironmentRequest() {
    return TargetEnvironmentConfigurations.createEnvironmentRequest(myRunProfile, myProject);
  }

  @ApiStatus.Experimental
  public @NotNull TargetEnvironment getPreparedTargetEnvironment(@NotNull RunProfileState runProfileState,
                                                                 @NotNull TargetProgressIndicator targetProgressIndicator)
    throws ExecutionException {
    if (myPrepareRemoteEnvironment != null) {
      // In a correct implementation that uses the new API this condition is always true.
      return myPrepareRemoteEnvironment;
    }
    // Warning: this method executes in EDT!
    return prepareTargetEnvironment(runProfileState, targetProgressIndicator);
  }

  @ApiStatus.Experimental
  public @NotNull TargetEnvironment prepareTargetEnvironment(@NotNull RunProfileState runProfileState,
                                                             @NotNull TargetProgressIndicator targetProgressIndicator)
    throws ExecutionException {
    TargetEnvironmentRequest request = null;
    if (runProfileState instanceof TargetEnvironmentAwareRunProfileState &&
        myRunProfile instanceof TargetEnvironmentAwareRunProfile &&
        TargetEnvironmentConfigurations.getEffectiveTargetName((TargetEnvironmentAwareRunProfile)myRunProfile, myProject) == null) {
      request = ((TargetEnvironmentAwareRunProfileState)runProfileState).createCustomTargetEnvironmentRequest();
    }
    if (request == null) {
      request = getTargetEnvironmentRequest();
    }
    if (runProfileState instanceof TargetEnvironmentAwareRunProfileState) {
      ((TargetEnvironmentAwareRunProfileState)runProfileState)
        .prepareTargetEnvironmentRequest(request, targetProgressIndicator);
    }
    myPrepareRemoteEnvironment = request.prepareEnvironment(targetProgressIndicator);
    if (runProfileState instanceof TargetEnvironmentAwareRunProfileState) {
      ((TargetEnvironmentAwareRunProfileState)runProfileState)
        .handleCreatedTargetEnvironment(myPrepareRemoteEnvironment, targetProgressIndicator);
    }
    return myPrepareRemoteEnvironment;
  }

  @ApiStatus.Internal
  public void setCallback(@Nullable ProgramRunner.Callback callback) {
    this.callback = callback;
  }

  public @Nullable ProgramRunner.Callback getCallback() {
    return callback;
  }

  @Override
  public void dispose() {
    myContentToReuse = null;
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  public @NotNull ExecutionTarget getExecutionTarget() {
    return myTarget;
  }

  public @NotNull RunProfile getRunProfile() {
    return myRunProfile;
  }

  public @Nullable RunnerAndConfigurationSettings getRunnerAndConfigurationSettings() {
    return myRunnerAndConfigurationSettings;
  }

  public @Nullable RunContentDescriptor getContentToReuse() {
    return myContentToReuse;
  }

  public void setContentToReuse(@Nullable RunContentDescriptor contentToReuse) {
    myContentToReuse = contentToReuse;

    if (contentToReuse != null) {
      Disposer.register(contentToReuse, this);
    }
  }

  public @NotNull ProgramRunner<?> getRunner() {
    return myRunner;
  }

  public @Nullable RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  public @Nullable ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  public @Nullable RunProfileState getState() throws ExecutionException {
    return myRunProfile.getState(myExecutor, this);
  }

  public long assignNewExecutionId() {
    myExecutionId = myIdHolder.incrementAndGet();
    return myExecutionId;
  }

  public void setExecutionId(long executionId) {
    myExecutionId = executionId;
  }

  /**
   * By default, a new unique execution ID is assigned to each new {@link ExecutionEnvironment} (see {@link #assignNewExecutionId}).
   * Can be set manually to create a batch of {@link ExecutionEnvironment} that are semantically a "single launch".
   * {@link RunContentDescriptor}s will not reuse each other tabs if they have the same execution ID.
   *
   * @return An id that will be propagated to resulting {@link RunContentDescriptor}.
   */
  public long getExecutionId() {
    return myExecutionId;
  }

  public @NotNull Executor getExecutor() {
    return myExecutor;
  }

  @Override
  public String toString() {
    if (myRunnerAndConfigurationSettings != null) {
      return myRunnerAndConfigurationSettings.getName();
    }
    return myRunProfile.getName();
  }

  @ApiStatus.Experimental
  public boolean isHeadless() {
    return isHeadless;
  }

  @ApiStatus.Experimental
  public void setHeadless() {
    isHeadless = true;
  }

  @ApiStatus.Internal
  public void setDataContext(@NotNull DataContext dataContext) {
    myDataContext = CustomizedDataContext.withSnapshot(IdeUiService.getInstance().createAsyncDataContext(dataContext), sink -> {
      Module module = null;
      if (myRunnerAndConfigurationSettings != null &&
          myRunnerAndConfigurationSettings.getConfiguration() instanceof ModuleBasedConfiguration<?, ?> configuration) {
        module = configuration.getConfigurationModule().getModule();
      }
      if (module != null) {
        sink.set(PlatformCoreDataKeys.MODULE, module);
      }
      else {
        sink.setNull(PlatformCoreDataKeys.MODULE);
      }
    });
  }

  public @Nullable DataContext getDataContext() {
    return myDataContext;
  }


  void setModulePath(@NotNull String modulePath) {
    this.myModulePath = modulePath;
  }

  public @Nullable String getModulePath() {
    return myModulePath;
  }

  /**
   * @return A valid executionId that was not previously assigned to any {@link ExecutionEnvironment}.
   */
  public static long getNextUnusedExecutionId() {
    return myIdHolder.incrementAndGet();
  }

  public boolean isRunningCurrentFile() {
    return myRunningCurrentFile;
  }

  public void setRunningCurrentFile(boolean runningCurrentFile) {
    myRunningCurrentFile = runningCurrentFile;
  }
}
