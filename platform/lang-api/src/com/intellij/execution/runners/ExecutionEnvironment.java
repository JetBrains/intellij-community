// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.target.*;
import com.intellij.execution.target.local.LocalTargetEnvironmentFactory;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.application.Experiments;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.actionSystem.LangDataKeys.*;

public final class ExecutionEnvironment extends UserDataHolderBase implements Disposable {
  private static final AtomicLong myIdHolder = new AtomicLong(1L);

  @NotNull private final Project myProject;

  @NotNull private RunProfile myRunProfile;
  @NotNull private final Executor myExecutor;

  @NotNull private ExecutionTarget myTarget;
  private TargetEnvironmentFactory myTargetEnvironmentFactory;
  private volatile TargetEnvironment myPrepareRemoteEnvironment;

  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private final RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  @Nullable private RunContentDescriptor myContentToReuse;
  private final ProgramRunner<?> myRunner;
  private long myExecutionId = 0;
  @Nullable private DataContext myDataContext;

  @Nullable
  private ProgramRunner.Callback callback;

  @TestOnly
  public ExecutionEnvironment() {
    myProject = null;
    myContentToReuse = null;
    myRunnerAndConfigurationSettings = null;
    myExecutor = null;
    myRunner = null;
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

  public @NotNull TargetEnvironmentFactory getTargetEnvironmentFactory() {
    if (myTargetEnvironmentFactory != null) {
      return myTargetEnvironmentFactory;
    }
    return myTargetEnvironmentFactory = createTargetEnvironmentFactory();
  }

  @NotNull
  private TargetEnvironmentFactory createTargetEnvironmentFactory() {
    if (myRunProfile instanceof TargetEnvironmentAwareRunProfile &&
        Experiments.getInstance().isFeatureEnabled("run.targets")) {
      String targetName = ((TargetEnvironmentAwareRunProfile)myRunProfile).getDefaultTargetName();
      if (targetName != null) {
        TargetEnvironmentConfiguration config = TargetEnvironmentsManager.getInstance().getTargets().findByName(targetName);
        if (config != null) {
          return config.createEnvironmentFactory(myProject);
        }
      }
    }
    return new LocalTargetEnvironmentFactory();
  }

  @ApiStatus.Experimental
  public @NotNull TargetEnvironment getPreparedTargetEnvironment(@NotNull RunProfileState runProfileState, @NotNull ProgressIndicator progressIndicator)
    throws ExecutionException {
    if (myPrepareRemoteEnvironment != null) {
      // In a correct implementation that uses the new API this condition is always true.
      return myPrepareRemoteEnvironment;
    }
    // Warning: this method executes in EDT!
    return prepareTargetEnvironment(runProfileState, progressIndicator);
  }

  @ApiStatus.Experimental
  public @NotNull TargetEnvironment prepareTargetEnvironment(@NotNull RunProfileState runProfileState, @NotNull ProgressIndicator progressIndicator)
    throws ExecutionException {
    TargetEnvironmentFactory factory = getTargetEnvironmentFactory();
    TargetEnvironmentRequest request = factory.createRequest();
    if (runProfileState instanceof TargetEnvironmentAwareRunProfileState) {
      ((TargetEnvironmentAwareRunProfileState)runProfileState)
        .prepareTargetEnvironmentRequest(request, factory.getTargetConfiguration(), progressIndicator);
    }
    myPrepareRemoteEnvironment = factory.prepareRemoteEnvironment(request, progressIndicator);
    if (runProfileState instanceof TargetEnvironmentAwareRunProfileState) {
      ((TargetEnvironmentAwareRunProfileState)runProfileState)
        .handleCreatedTargetEnvironment(myPrepareRemoteEnvironment, progressIndicator);
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
   * By default a new unique executionId is assigned to each new {@link ExecutionEnvironment} ({@see assignNewExecutionId}).
   * Can be set manually to create a batch of {@link ExecutionEnvironment} that are semantically a "single launch".
   * {@link RunContentDescriptor}s will not reuse each other tabs if they have the same executionId.
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
    else if (myRunProfile != null) {
      return myRunProfile.getName();
    }
    else if (myContentToReuse != null) {
      return myContentToReuse.getDisplayName();
    }
    return super.toString();
  }

  void setDataContext(@NotNull DataContext dataContext) {
    myDataContext = CachingDataContext.cacheIfNeeded(dataContext);
  }

  @Nullable
  public DataContext getDataContext() {
    return myDataContext;
  }

  private static class CachingDataContext implements DataContext {
    private static final DataKey[] keys = {PROJECT, PROJECT_FILE_DIRECTORY, EDITOR, VIRTUAL_FILE, MODULE, PSI_FILE};
    private final Map<String, Object> values = new HashMap<>();

    @NotNull
    static CachingDataContext cacheIfNeeded(@NotNull DataContext context) {
      if (context instanceof CachingDataContext)
        return (CachingDataContext)context;
      return new CachingDataContext(context);
    }

    private CachingDataContext(DataContext context) {
      for (DataKey key : keys) {
        values.put(key.getName(), key.getData(context));
      }
    }

    @Override
    public Object getData(@NotNull @NonNls String dataId) {
        return values.get(dataId);
    }
  }

  /**
   * @return A valid executionId that was not previously assigned to any {@link ExecutionEnvironment}.
   */
  public static long getNextUnusedExecutionId() {
    return myIdHolder.incrementAndGet();
  }
}
