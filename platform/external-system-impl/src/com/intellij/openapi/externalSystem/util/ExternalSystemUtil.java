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
package com.intellij.openapi.externalSystem.util;

import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.externalSystem.service.ImportCanceledException;
import com.intellij.openapi.externalSystem.service.execution.AbstractExternalSystemTaskConfigurationType;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.settings.ExternalSystemConfigLocator;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.util.Consumer;
import com.intellij.util.DisposeAwareRunnable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.UIUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction;

/**
 * @author Denis Zhdanov
 * @since 4/22/13 9:36 AM
 */
public class ExternalSystemUtil {

  private static final Logger LOG = Logger.getInstance("#" + ExternalSystemUtil.class.getName());

  @NotNull private static final Map<String, String> RUNNER_IDS = ContainerUtilRt.newHashMap();

  public static final TObjectHashingStrategy<Pair<ProjectSystemId, File>> HASHING_STRATEGY =
    new TObjectHashingStrategy<Pair<ProjectSystemId, File>>() {
      @Override
      public int computeHashCode(Pair<ProjectSystemId, File> object) {
        return object.first.hashCode() + fileHashCode(object.second);
      }

      @Override
      public boolean equals(Pair<ProjectSystemId, File> o1, Pair<ProjectSystemId, File> o2) {
        return o1.first.equals(o2.first) && filesEqual(o1.second, o2.second);
      }
    };

  static {
    RUNNER_IDS.put(DefaultRunExecutor.EXECUTOR_ID, ExternalSystemConstants.RUNNER_ID);
    RUNNER_IDS.put(DefaultDebugExecutor.EXECUTOR_ID, ExternalSystemConstants.DEBUG_RUNNER_ID);
  }

  private ExternalSystemUtil() {
  }

  public static int fileHashCode(@Nullable File file) {
    int hash;
    try {
      hash = FileUtil.pathHashCode(file == null ? null : file.getCanonicalPath());
    }
    catch (IOException e) {
      LOG.warn("unable to get canonical file path", e);
      hash = FileUtil.fileHashCode(file);
    }
    return hash;
  }

  public static boolean filesEqual(@Nullable File file1, @Nullable File file2) {
    try {
      return FileUtil.pathsEqual(file1 == null ? null : file1.getCanonicalPath(), file2 == null ? null : file2.getCanonicalPath());
    }
    catch (IOException e) {
      LOG.warn("unable to get canonical file path", e);
    }
    return FileUtil.filesEqual(file1, file2);
  }

  public static void ensureToolWindowInitialized(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    try {
      ToolWindowManager manager = ToolWindowManager.getInstance(project);
      if (!(manager instanceof ToolWindowManagerEx)) {
        return;
      }
      ToolWindowManagerEx managerEx = (ToolWindowManagerEx)manager;
      String id = externalSystemId.getReadableName();
      ToolWindow window = manager.getToolWindow(id);
      if (window != null) {
        return;
      }
      ToolWindowEP[] beans = Extensions.getExtensions(ToolWindowEP.EP_NAME);
      for (final ToolWindowEP bean : beans) {
        if (id.equals(bean.id)) {
          managerEx.initToolWindow(bean);
        }
      }
    }
    catch (Exception e) {
      LOG.error(String.format("Unable to initialize %s tool window", externalSystemId.getReadableName()), e);
    }
  }

  @Nullable
  public static ToolWindow ensureToolWindowContentInitialized(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    final ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager == null) return null;

    final ToolWindow toolWindow = toolWindowManager.getToolWindow(externalSystemId.getReadableName());
    if (toolWindow == null) return null;

    if (toolWindow instanceof ToolWindowImpl) {
      ((ToolWindowImpl)toolWindow).ensureContentInitialized();
    }
    return toolWindow;
  }

  /**
   * Asks to refresh all external projects of the target external system linked to the given ide project.
   * <p/>
   * 'Refresh' here means 'obtain the most up-to-date version and apply it to the ide'.
   *
   * @param project          target ide project
   * @param externalSystemId target external system which projects should be refreshed
   * @param force            flag which defines if external project refresh should be performed if it's config is up-to-date
   * @deprecated use {@link  ExternalSystemUtil#refreshProjects(ImportSpecBuilder)}
   */
  @Deprecated
  public static void refreshProjects(@NotNull final Project project, @NotNull final ProjectSystemId externalSystemId, boolean force) {
    refreshProjects(project, externalSystemId, force, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }

  /**
   * Asks to refresh all external projects of the target external system linked to the given ide project.
   * <p/>
   * 'Refresh' here means 'obtain the most up-to-date version and apply it to the ide'.
   *
   * @param project           target ide project
   * @param externalSystemId  target external system which projects should be refreshed
   * @param force             flag which defines if external project refresh should be performed if it's config is up-to-date
   *
   * @deprecated use {@link  ExternalSystemUtil#refreshProjects(ImportSpecBuilder)}
   */
  @Deprecated
  public static void refreshProjects(@NotNull final Project project, @NotNull final ProjectSystemId externalSystemId, boolean force, @NotNull final ProgressExecutionMode progressExecutionMode) {
    refreshProjects(
      new ImportSpecBuilder(project, externalSystemId)
        .forceWhenUptodate(force)
        .use(progressExecutionMode)
    );
  }

  /**
   * Asks to refresh all external projects of the target external system linked to the given ide project based on provided spec
   *
   * @param specBuilder import specification builder
   */
  public static void refreshProjects(@NotNull final ImportSpecBuilder specBuilder) {
    ImportSpec spec = specBuilder.build();

    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(spec.getExternalSystemId());
    if (manager == null) {
      return;
    }
    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(spec.getProject());
    final Collection<? extends ExternalProjectSettings> projectsSettings = settings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return;
    }

    final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);

    final ExternalProjectRefreshCallback callback;
    if (spec.getCallback() == null) {
      callback = new MyMultiExternalProjectRefreshCallback(spec.getProject(), projectDataManager, spec.getExternalSystemId());
    }
    else {
      callback = spec.getCallback();
    }

    Map<String, Long> modificationStamps =
      manager.getLocalSettingsProvider().fun(spec.getProject()).getExternalConfigModificationStamps();
    Set<String> toRefresh = ContainerUtilRt.newHashSet();
    for (ExternalProjectSettings setting : projectsSettings) {

      // don't refresh project when auto-import is disabled if such behavior needed (e.g. on project opening when auto-import is disabled)
      if (!setting.isUseAutoImport() && spec.isWhenAutoImportEnabled()) continue;

      if (spec.isForceWhenUptodate()) {
        toRefresh.add(setting.getExternalProjectPath());
      }
      else {
        Long oldModificationStamp = modificationStamps.get(setting.getExternalProjectPath());
        long currentModificationStamp = getTimeStamp(setting, spec.getExternalSystemId());
        if (oldModificationStamp == null || oldModificationStamp < currentModificationStamp) {
          toRefresh.add(setting.getExternalProjectPath());
        }
      }
    }

    if (!toRefresh.isEmpty()) {
      ExternalSystemNotificationManager.getInstance(spec.getProject())
        .clearNotifications(null, NotificationSource.PROJECT_SYNC, spec.getExternalSystemId());

      for (String path : toRefresh) {
        refreshProject(
          spec.getProject(), spec.getExternalSystemId(), path, callback, false, spec.getProgressExecutionMode());
      }
    }
  }

  private static long getTimeStamp(@NotNull ExternalProjectSettings externalProjectSettings, @NotNull ProjectSystemId externalSystemId) {
    long timeStamp = 0;
    for (ExternalSystemConfigLocator locator : ExternalSystemConfigLocator.EP_NAME.getExtensions()) {
      if (!externalSystemId.equals(locator.getTargetExternalSystemId())) {
        continue;
      }
      for (VirtualFile virtualFile : locator.findAll(externalProjectSettings)) {
        timeStamp += virtualFile.getTimeStamp();
      }
    }
    return timeStamp;
  }

  @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
  @Nullable
  private static String extractDetails(@NotNull Throwable e) {
    final Throwable unwrapped = RemoteUtil.unwrap(e);
    if (unwrapped instanceof ExternalSystemException) {
      return ((ExternalSystemException)unwrapped).getOriginalReason();
    }
    return null;
  }

  public static void refreshProject(@NotNull final Project project,
                                    @NotNull final ProjectSystemId externalSystemId,
                                    @NotNull final String externalProjectPath,
                                    final boolean isPreviewMode,
                                    @NotNull final ProgressExecutionMode progressExecutionMode) {
    refreshProject(project, externalSystemId, externalProjectPath, new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          return;
        }
        final boolean synchronous = progressExecutionMode == ProgressExecutionMode.MODAL_SYNC;
        ServiceManager.getService(ProjectDataManager.class).importData(externalProject, project, synchronous);
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
      }
    }, isPreviewMode, progressExecutionMode, true);
  }

  /**
   * TODO[Vlad]: refactor the method to use {@link ImportSpecBuilder}
   *
   * Queries slave gradle process to refresh target gradle project.
   *
   * @param project               target intellij project to use
   * @param externalProjectPath   path of the target gradle project's file
   * @param callback              callback to be notified on refresh result
   * @param isPreviewMode         flag that identifies whether gradle libraries should be resolved during the refresh
   * @return the most up-to-date gradle project (if any)
   */
  public static void refreshProject(@NotNull final Project project,
                                    @NotNull final ProjectSystemId externalSystemId,
                                    @NotNull final String externalProjectPath,
                                    @NotNull final ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    @NotNull final ProgressExecutionMode progressExecutionMode) {
    refreshProject(project, externalSystemId, externalProjectPath, callback, isPreviewMode, progressExecutionMode, true);
  }

  /**
   * TODO[Vlad]: refactor the method to use {@link ImportSpecBuilder}
   *
   * Queries slave gradle process to refresh target gradle project.
   *
   * @param project               target intellij project to use
   * @param externalProjectPath   path of the target gradle project's file
   * @param callback              callback to be notified on refresh result
   * @param isPreviewMode         flag that identifies whether gradle libraries should be resolved during the refresh
   * @param reportRefreshError    prevent to show annoying error notification, e.g. if auto-import mode used
   * @return the most up-to-date gradle project (if any)
   */
  public static void refreshProject(@NotNull final Project project,
                                    @NotNull final ProjectSystemId externalSystemId,
                                    @NotNull final String externalProjectPath,
                                    @NotNull final ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    @NotNull final ProgressExecutionMode progressExecutionMode,
                                    final boolean reportRefreshError)
  {
    File projectFile = new File(externalProjectPath);
    final String projectName;
    if (projectFile.isFile()) {
      projectName = projectFile.getParentFile().getName();
    }
    else {
      projectName = projectFile.getName();
    }
    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {
      private final ExternalSystemResolveProjectTask myTask
        = new ExternalSystemResolveProjectTask(externalSystemId, project, externalProjectPath, isPreviewMode);

      @SuppressWarnings({"ThrowableResultOfMethodCallIgnored", "IOResourceOpenedButNotSafelyClosed"})
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        if(project.isDisposed()) return;

        if (indicator instanceof ProgressIndicatorEx) {
          ((ProgressIndicatorEx)indicator).addStateDelegate(new AbstractProgressIndicatorExBase() {
            @Override
            public void cancel() {
              super.cancel();

              ApplicationManager.getApplication().executeOnPooledThread(
                (Runnable)() -> myTask.cancel(ExternalSystemTaskNotificationListener.EP_NAME.getExtensions()));
            }
          });
        }

        ExternalSystemProcessingManager processingManager = ServiceManager.getService(ExternalSystemProcessingManager.class);
        if (processingManager.findTask(ExternalSystemTaskType.RESOLVE_PROJECT, externalSystemId, externalProjectPath) != null) {
          callback.onFailure(ExternalSystemBundle.message("error.resolve.already.running", externalProjectPath), null);
          return;
        }

        if (!(callback instanceof MyMultiExternalProjectRefreshCallback)) {
          ExternalSystemNotificationManager.getInstance(project)
            .clearNotifications(null, NotificationSource.PROJECT_SYNC, externalSystemId);
        }

        final ExternalSystemTaskActivator externalSystemTaskActivator = ExternalProjectsManager.getInstance(project).getTaskActivator();
        if (!isPreviewMode && !externalSystemTaskActivator.runTasks(externalProjectPath, ExternalSystemTaskActivator.Phase.BEFORE_SYNC)) {
          return;
        }

        myTask.execute(indicator, ExternalSystemTaskNotificationListener.EP_NAME.getExtensions());
        if(project.isDisposed()) return;

        final Throwable error = myTask.getError();
        if (error == null) {
          ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
          assert manager != null;
          DataNode<ProjectData> externalProject = myTask.getExternalProject();

          if(externalProject != null) {
            Set<String> externalModulePaths = ContainerUtil.newHashSet();
            Collection<DataNode<ModuleData>> moduleNodes = ExternalSystemApiUtil.findAll(externalProject, ProjectKeys.MODULE);
            for (DataNode<ModuleData> node : moduleNodes) {
              externalModulePaths.add(node.getData().getLinkedExternalProjectPath());
            }

            String projectPath = externalProject.getData().getLinkedExternalProjectPath();
            ExternalProjectSettings linkedProjectSettings = manager.getSettingsProvider().fun(project).getLinkedProjectSettings(projectPath);
            if (linkedProjectSettings != null) {
              linkedProjectSettings.setModules(externalModulePaths);

              long stamp = getTimeStamp(linkedProjectSettings, externalSystemId);
              if (stamp > 0) {
                manager.getLocalSettingsProvider().fun(project).getExternalConfigModificationStamps().put(externalProjectPath, stamp);
              }
            }
          }

          callback.onSuccess(externalProject);

          if(!isPreviewMode) {
            externalSystemTaskActivator.runTasks(externalProjectPath, ExternalSystemTaskActivator.Phase.AFTER_SYNC);
          }
          return;
        }
        if(error instanceof ImportCanceledException) {
          // stop refresh task
          return;
        }
        String message = ExternalSystemApiUtil.buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          message = String.format(
            "Can't resolve %s project at '%s'. Reason: %s", externalSystemId.getReadableName(), externalProjectPath, message
          );
        }

        callback.onFailure(message, extractDetails(error));

        ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(externalSystemId);
        if(manager == null) {
          return;
        }
        AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(project);
        ExternalProjectSettings projectSettings = settings.getLinkedProjectSettings(externalProjectPath);
        if (projectSettings == null || !reportRefreshError) {
          return;
        }

        ExternalSystemNotificationManager.getInstance(project).processExternalProjectRefreshError(error, projectName, externalSystemId);
      }
    };

    ExternalSystemApiUtil.executeOnEdt(true, () -> {
      final String title;
      switch (progressExecutionMode) {
        case MODAL_SYNC:
          title = ExternalSystemBundle.message("progress.import.text", projectName, externalSystemId.getReadableName());
          new Task.Modal(project, title, true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
            }
          }.queue();
          break;
        case IN_BACKGROUND_ASYNC:
          title = ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemId.getReadableName());
          new Task.Backgroundable(project, title) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
            }
          }.queue();
          break;
        case START_IN_FOREGROUND_ASYNC:
          title = ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemId.getReadableName());
          new Task.Backgroundable(project, title, true, PerformInBackgroundOption.DEAF) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
              refreshProjectStructureTask.execute(indicator);
            }
          }.queue();
      }
    });
  }

  public static void runTask(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             @NotNull String executorId,
                             @NotNull Project project,
                             @NotNull ProjectSystemId externalSystemId) {
    runTask(taskSettings, executorId, project, externalSystemId, null, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }

  public static void runTask(@NotNull final ExternalSystemTaskExecutionSettings taskSettings,
                             @NotNull final String executorId,
                             @NotNull final Project project,
                             @NotNull final ProjectSystemId externalSystemId,
                             @Nullable final TaskCallback callback,
                             @NotNull final ProgressExecutionMode progressExecutionMode) {
    final Pair<ProgramRunner, ExecutionEnvironment> pair = createRunner(taskSettings, executorId, project, externalSystemId);
    if (pair == null) return;

    final ProgramRunner runner = pair.first;
    final ExecutionEnvironment environment = pair.second;

    final TaskUnderProgress task = new TaskUnderProgress() {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        final Semaphore targetDone = new Semaphore();
        final Ref<Boolean> result = new Ref<Boolean>(false);
        final Disposable disposable = Disposer.newDisposable();

        project.getMessageBus().connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionAdapter() {
          public void processStartScheduled(final String executorIdLocal, final ExecutionEnvironment environmentLocal) {
            if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
              targetDone.down();
            }
          }

          public void processNotStarted(final String executorIdLocal, @NotNull final ExecutionEnvironment environmentLocal) {
            if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
              targetDone.up();
            }
          }

          public void processStarted(final String executorIdLocal,
                                     @NotNull final ExecutionEnvironment environmentLocal,
                                     @NotNull final ProcessHandler handler) {
            if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
              handler.addProcessListener(new ProcessAdapter() {
                public void processTerminated(ProcessEvent event) {
                  result.set(event.getExitCode() == 0);
                  targetDone.up();
                }
              });
            }
          }
        });

        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
              runner.execute(environment);
            }
            catch (ExecutionException e) {
              targetDone.up();
              LOG.error(e);
            }
          }, ModalityState.NON_MODAL);
        }
        catch (Exception e) {
          LOG.error(e);
          Disposer.dispose(disposable);
          return;
        }

        targetDone.waitFor();
        Disposer.dispose(disposable);

        if (callback != null) {
          if (result.get()) {
            callback.onSuccess();
          }
          else {
            callback.onFailure();
          }
        }
      }
    };

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        final String title = AbstractExternalSystemTaskConfigurationType.generateName(project, taskSettings);
        switch (progressExecutionMode) {
          case MODAL_SYNC:
            new Task.Modal(project, title, true) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                task.execute(indicator);
              }
            }.queue();
            break;
          case IN_BACKGROUND_ASYNC:
            new Task.Backgroundable(project, title) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                task.execute(indicator);
              }
            }.queue();
            break;
          case START_IN_FOREGROUND_ASYNC:
            new Task.Backgroundable(project, title, true, PerformInBackgroundOption.DEAF) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                task.execute(indicator);
              }
            }.queue();
        }
      }
    });
  }

  @Nullable
  public static Pair<ProgramRunner, ExecutionEnvironment> createRunner(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                                                                       @NotNull String executorId,
                                                                       @NotNull Project project,
                                                                       @NotNull ProjectSystemId externalSystemId) {
    Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    if (executor == null) return null;

    String runnerId = getRunnerId(executorId);
    if (runnerId == null) return null;

    ProgramRunner runner = RunnerRegistry.getInstance().findRunnerById(runnerId);
    if (runner == null) return null;

    RunnerAndConfigurationSettings settings = createExternalSystemRunnerAndConfigurationSettings(taskSettings, project, externalSystemId);
    if (settings == null) return null;

    return Pair.create(runner, new ExecutionEnvironment(executor, runner, settings, project));
  }

  @Nullable
  public static RunnerAndConfigurationSettings createExternalSystemRunnerAndConfigurationSettings(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                                                                                                  @NotNull Project project,
                                                                                                  @NotNull ProjectSystemId externalSystemId) {
    AbstractExternalSystemTaskConfigurationType configurationType = findConfigurationType(externalSystemId);
    if (configurationType == null) return null;

    String name = AbstractExternalSystemTaskConfigurationType.generateName(project, taskSettings);
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).createRunConfiguration(name, configurationType.getFactory());
    ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)settings.getConfiguration();
    runConfiguration.getSettings().setExternalProjectPath(taskSettings.getExternalProjectPath());
    runConfiguration.getSettings().setTaskNames(ContainerUtil.newArrayList(taskSettings.getTaskNames()));
    runConfiguration.getSettings().setTaskDescriptions(ContainerUtil.newArrayList(taskSettings.getTaskDescriptions()));
    runConfiguration.getSettings().setVmOptions(taskSettings.getVmOptions());
    runConfiguration.getSettings().setScriptParameters(taskSettings.getScriptParameters());
    runConfiguration.getSettings().setExecutionName(taskSettings.getExecutionName());

    return settings;
  }

  @Nullable
  public static AbstractExternalSystemTaskConfigurationType findConfigurationType(@NotNull ProjectSystemId externalSystemId) {
    for (ConfigurationType type : Extensions.getExtensions(ConfigurationType.CONFIGURATION_TYPE_EP)) {
      if (type instanceof AbstractExternalSystemTaskConfigurationType) {
        AbstractExternalSystemTaskConfigurationType candidate = (AbstractExternalSystemTaskConfigurationType)type;
        if (externalSystemId.equals(candidate.getExternalSystemId())) {
          return candidate;
        }
      }
    }
    return null;
  }

  @Nullable
  public static String getRunnerId(@NotNull String executorId) {
    return RUNNER_IDS.get(executorId);
  }

  /**
   * Tries to obtain external project info implied by the given settings and link that external project to the given ide project. 
   * 
   * @param externalSystemId         target external system
   * @param projectSettings          settings of the external project to link
   * @param project                  target ide project to link external project to
   * @param executionResultCallback  it might take a while to resolve external project info, that's why it's possible to provide
   *                                 a callback to be notified on processing result. It receives <code>true</code> if an external
   *                                 project has been successfully linked to the given ide project;
   *                                 <code>false</code> otherwise (note that corresponding notification with error details is expected
   *                                 to be shown to the end-user then)
   * @param isPreviewMode            flag which identifies if missing external project binaries should be downloaded
   * @param progressExecutionMode         identifies how progress bar will be represented for the current processing
   */
  @SuppressWarnings("UnusedDeclaration")
  public static void linkExternalProject(@NotNull final ProjectSystemId externalSystemId,
                                         @NotNull final ExternalProjectSettings projectSettings,
                                         @NotNull final Project project,
                                         @Nullable final Consumer<Boolean> executionResultCallback,
                                         boolean isPreviewMode,
                                         @NotNull final ProgressExecutionMode progressExecutionMode)
  {
    ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {
      @SuppressWarnings("unchecked")
      @Override
      public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          if (executionResultCallback != null) {
            executionResultCallback.consume(false);
          }
          return;
        }
        AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, externalSystemId);
        Set<ExternalProjectSettings> projects = ContainerUtilRt.newHashSet(systemSettings.getLinkedProjectsSettings());
        projects.add(projectSettings);
        systemSettings.setLinkedProjectsSettings(projects);
        ensureToolWindowInitialized(project, externalSystemId);
        ServiceManager.getService(ProjectDataManager.class).importData(externalProject, project, true);
        if (executionResultCallback != null) {
          executionResultCallback.consume(true);
        }
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        if (executionResultCallback != null) {
          executionResultCallback.consume(false);
        }
      }
    };
    refreshProject(project, externalSystemId, projectSettings.getExternalProjectPath(), callback, isPreviewMode, progressExecutionMode);
  }

  @Nullable
  public static VirtualFile refreshAndFindFileByIoFile(@NotNull final File file) {
    final Application app = ApplicationManager.getApplication();
    if(app.isDispatchThread()) {
      return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    } else {
      assert !((ApplicationEx)app).holdsReadLock();
      return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    }
  }

  @Nullable
  public static VirtualFile findLocalFileByPath(String path) {
    VirtualFile result = StandardFileSystems.local().findFileByPath(path);
    if (result != null) return result;

    return !ApplicationManager.getApplication().isReadAccessAllowed()
           ? findLocalFileByPathUnderWriteAction(path)
           : findLocalFileByPathUnderReadAction(path);
  }

  @Nullable
  private static VirtualFile findLocalFileByPathUnderWriteAction(final String path) {
    return doWriteAction(() -> StandardFileSystems.local().refreshAndFindFileByPath(path));
  }

  @Nullable
  private static VirtualFile findLocalFileByPathUnderReadAction(final String path) {
    return ApplicationManager.getApplication().runReadAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return StandardFileSystems.local().findFileByPath(path);
      }
    });
  }

  public static void scheduleExternalViewStructureUpdate(@NotNull final Project project, @NotNull final ProjectSystemId systemId) {
    ExternalProjectsView externalProjectsView = ExternalProjectsManager.getInstance(project).getExternalProjectsView(systemId);
    if (externalProjectsView instanceof ExternalProjectsViewImpl) {
      ((ExternalProjectsViewImpl)externalProjectsView).scheduleStructureUpdate();
    }
  }

  @Nullable
  public static ExternalProjectInfo getExternalProjectInfo(@NotNull final Project project,
                                                           @NotNull final ProjectSystemId projectSystemId,
                                                           @NotNull final String externalProjectPath) {
    final ExternalProjectSettings linkedProjectSettings =
      ExternalSystemApiUtil.getSettings(project, projectSystemId).getLinkedProjectSettings(externalProjectPath);
    if (linkedProjectSettings == null) return null;

    return ProjectDataManager.getInstance().getExternalProjectData(
      project, projectSystemId, linkedProjectSettings.getExternalProjectPath());
  }


  public static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(DisposeAwareRunnable.create(r, p), state);
    }
  }

  public static boolean isNoBackgroundMode() {
    return (ApplicationManager.getApplication().isUnitTestMode()
            || ApplicationManager.getApplication().isHeadlessEnvironment());
  }

  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }

  private static class MyMultiExternalProjectRefreshCallback implements ExternalProjectRefreshCallback {

    @NotNull
    private final Set<String> myExternalModulePaths;
    private final Project myProject;
    private final ProjectDataManager myProjectDataManager;
    private final ProjectSystemId myExternalSystemId;

    public MyMultiExternalProjectRefreshCallback(Project project,
                                                 ProjectDataManager projectDataManager,
                                                 ProjectSystemId externalSystemId) {
      myProject = project;
      myProjectDataManager = projectDataManager;
      myExternalSystemId = externalSystemId;
      myExternalModulePaths = ContainerUtilRt.newHashSet();
    }

    @Override
    public void onSuccess(@Nullable final DataNode<ProjectData> externalProject) {
      if (externalProject == null) {
        return;
      }
      Collection<DataNode<ModuleData>> moduleNodes = ExternalSystemApiUtil.findAllRecursively(externalProject, ProjectKeys.MODULE);
      for (DataNode<ModuleData> node : moduleNodes) {
        myExternalModulePaths.add(node.getData().getLinkedExternalProjectPath());
      }

      myProjectDataManager.importData(externalProject, myProject, true);
    }

    @Override
    public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
    }
  }
}
