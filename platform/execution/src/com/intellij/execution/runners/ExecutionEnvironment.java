// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @NotNull private final Project myProject;

  @NotNull private final RunProfile myRunProfile;
  @NotNull private final Executor myExecutor;

  @NotNull private final ExecutionTarget myTarget;
  private TargetEnvironmentRequest myTargetEnvironmentRequest;
  private volatile TargetEnvironment myPrepareRemoteEnvironment;

  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private final RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  @Nullable private RunContentDescriptor myContentToReuse;
  private final ProgramRunner<?> myRunner;
  private long myExecutionId = 0;
  @Nullable private DataContext myDataContext;
  @Nullable private String myModulePath;

  @Nullable
  private ProgramRunner.Callback callback;
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

  @NotNull
  private TargetEnvironmentRequest createTargetEnvironmentRequest() {
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

  @Nullable
  public ProgramRunner.Callback getCallback() {
    return callback;
  }

  @Override
  public void dispose() {
    myContentToReuse = null;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  public ExecutionTarget getExecutionTarget() {
    return myTarget;
  }

  @NotNull
  public RunProfile getRunProfile() {
    return myRunProfile;
  }

  @Nullable
  public RunnerAndConfigurationSettings getRunnerAndConfigurationSettings() {
    return myRunnerAndConfigurationSettings;
  }

  @Nullable
  public RunContentDescriptor getContentToReuse() {
    return myContentToReuse;
  }

  public void setContentToReuse(@Nullable RunContentDescriptor contentToReuse) {
    myContentToReuse = contentToReuse;

    if (contentToReuse != null) {
      Disposer.register(contentToReuse, this);
    }
  }

  @NotNull
  public ProgramRunner<?> getRunner() {
    return myRunner;
  }

  @Nullable
  public RunnerSettings getRunnerSettings() {
    return myRunnerSettings;
  }

  @Nullable
  public ConfigurationPerRunnerSettings getConfigurationSettings() {
    return myConfigurationSettings;
  }

  @Nullable
  public RunProfileState getState() throws ExecutionException {
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

  @NotNull
  public Executor getExecutor() {
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
    myDataContext = CustomizedDataContext.create(IdeUiService.getInstance().createAsyncDataContext(dataContext), dataId -> {
      if (PlatformCoreDataKeys.MODULE.is(dataId)) {
        Module module = null;
        if (myRunnerAndConfigurationSettings != null &&
            myRunnerAndConfigurationSettings.getConfiguration() instanceof ModuleBasedConfiguration<?, ?> configuration) {
          module = configuration.getConfigurationModule().getModule();
        }
        return module == null ? CustomizedDataContext.EXPLICIT_NULL : module;
      }
      return null;
    });
  }

  @Nullable
  public DataContext getDataContext() {
    return myDataContext;
  }


  void setModulePath(@NotNull String modulePath) {
    this.myModulePath = modulePath;
  }

  @Nullable
  public String getModulePath() {
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
