/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.execution.runners;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationPerRunnerSettings;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.configurations.RunnerSettings;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.UserDataHolderBase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.intellij.openapi.actionSystem.LangDataKeys.*;

public class ExecutionEnvironment extends UserDataHolderBase implements Disposable {
  private static final AtomicLong myIdHolder = new AtomicLong(1L);

  @NotNull private final Project myProject;

  @NotNull private RunProfile myRunProfile;
  @NotNull private final Executor myExecutor;
  @NotNull private ExecutionTarget myTarget;

  @Nullable private RunnerSettings myRunnerSettings;
  @Nullable private ConfigurationPerRunnerSettings myConfigurationSettings;
  @Nullable private final RunnerAndConfigurationSettings myRunnerAndConfigurationSettings;
  @Nullable private RunContentDescriptor myContentToReuse;
  private final ProgramRunner<?> myRunner;
  private long myExecutionId = 0;
  @Nullable private DataContext myDataContext;

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
         runner);
  }

  ExecutionEnvironment(@NotNull RunProfile runProfile,
                       @NotNull Executor executor,
                       @NotNull ExecutionTarget target,
                       @NotNull Project project,
                       @Nullable RunnerSettings runnerSettings,
                       @Nullable ConfigurationPerRunnerSettings configurationSettings,
                       @Nullable RunContentDescriptor contentToReuse,
                       @Nullable RunnerAndConfigurationSettings settings,
                       @NotNull ProgramRunner<?> runner) {
    myExecutor = executor;
    myTarget = target;
    myRunProfile = runProfile;
    myRunnerSettings = runnerSettings;
    myConfigurationSettings = configurationSettings;
    myProject = project;
    setContentToReuse(contentToReuse);
    myRunnerAndConfigurationSettings = settings;

    myRunner = runner;
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
    myDataContext = CachingDataContext.cacheIfNeed(dataContext);
  }

  @Nullable
  public DataContext getDataContext() {
    return myDataContext;
  }

  private static class CachingDataContext implements DataContext {
    private static final DataKey[] keys = {PROJECT, PROJECT_FILE_DIRECTORY, EDITOR, VIRTUAL_FILE, MODULE, PSI_FILE};
    private final Map<String, Object> values = new HashMap<>();

    @NotNull
    static CachingDataContext cacheIfNeed(@NotNull DataContext context) {
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
    public Object getData(@NonNls String dataId) {
        return values.get(dataId);
    }
  }
}
