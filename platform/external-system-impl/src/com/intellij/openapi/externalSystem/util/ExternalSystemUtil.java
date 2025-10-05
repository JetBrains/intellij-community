// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.impl.*;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.icons.AllIcons;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemModulePropertyManager;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.issue.BuildIssueException;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent;
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemTaskExecutionEvent;
import com.intellij.openapi.externalSystem.service.ImportCanceledException;
import com.intellij.openapi.externalSystem.service.execution.*;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.*;
import com.intellij.openapi.externalSystem.service.project.trusted.ExternalSystemTrustedProjectDialog;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemStatUtilKt;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.IncompleteDependenciesService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.platform.backend.observation.TrackingUtil;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PlatformUtils;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager.createNotification;
import static com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings.SyncType.*;
import static org.jetbrains.annotations.Nls.Capitalization.Sentence;

public final class ExternalSystemUtil {
  private static final Logger LOG = Logger.getInstance(ExternalSystemUtil.class);

  private static final @NotNull Map<String, String> RUNNER_IDS = new HashMap<>();

  public static final HashingStrategy<Pair<ProjectSystemId, File>> HASHING_STRATEGY =
    new HashingStrategy<>() {
      @Override
      public int hashCode(Pair<ProjectSystemId, File> object) {
        return object.first.hashCode() + FileUtil.fileHashCode(object.second);
      }

      @Override
      public boolean equals(Pair<ProjectSystemId, File> o1, Pair<ProjectSystemId, File> o2) {
        return o1.first.equals(o2.first) && FileUtil.filesEqual(o1.second, o2.second);
      }
    };

  static {
    RUNNER_IDS.put(DefaultRunExecutor.EXECUTOR_ID, ExternalSystemConstants.RUNNER_ID);
    // DebugExecutor ID  - com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID
    String debugExecutorId = ToolWindowId.DEBUG;
    RUNNER_IDS.put(debugExecutorId, ExternalSystemConstants.DEBUG_RUNNER_ID);
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

  public static @Nullable ToolWindow ensureToolWindowContentInitialized(@NotNull Project project, @NotNull ProjectSystemId externalSystemId) {
    return ToolWindowManager.getInstance(project).getToolWindow(externalSystemId.getReadableName());
  }

  /**
   * Asks to refresh all external projects of the target external system linked to the given ide project based on provided spec
   *
   * @param specBuilder import specification builder
   */
  public static void refreshProjects(final @NotNull ImportSpecBuilder specBuilder) {
    refreshProjects(specBuilder.build());
  }

  /**
   * Asks to refresh all external projects of the target external system linked to the given ide project based on provided spec
   *
   * @param spec import specification
   */
  public static void refreshProjects(final @NotNull ImportSpec spec) {
    var manager = ExternalSystemApiUtil.getManager(spec.getExternalSystemId());
    if (manager == null) {
      return;
    }
    var settings = manager.getSettingsProvider().fun(spec.getProject());
    var projectsSettings = settings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return;
    }

    var externalProjectPaths = new HashSet<String>();
    for (var setting : projectsSettings) {
      externalProjectPaths.add(setting.getExternalProjectPath());
    }

    refreshProjectImpl(externalProjectPaths, spec);
  }

  private static @NotNull String extractDetails(@NotNull Throwable e) {
    var unwrapped = RemoteUtil.unwrap(e);
    if (unwrapped instanceof ExternalSystemException esException) {
      var reason = esException.getOriginalReason();
      if (!reason.isEmpty()) {
        return reason;
      }
    }
    return ExternalSystemApiUtil.stacktraceAsString(e);
  }

  /**
   * @deprecated use {@link ExternalSystemUtil#refreshProject(String, ImportSpec)} instead
   */
  @Deprecated
  public static void refreshProject(final @NotNull Project project,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull String externalProjectPath,
                                    final boolean isPreviewMode,
                                    final @NotNull ProgressExecutionMode progressExecutionMode) {
    var builder = new ImportSpecBuilder(project, externalSystemId)
      .use(progressExecutionMode)
      .withPreviewMode(isPreviewMode);
    refreshProject(externalProjectPath, builder);
  }

  /**
   * @deprecated use {@link ExternalSystemUtil#refreshProject(String, ImportSpec)} instead
   */
  @Deprecated
  public static void refreshProject(final @NotNull Project project,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull String externalProjectPath,
                                    final @NotNull ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    final @NotNull ProgressExecutionMode progressExecutionMode) {
    var builder = new ImportSpecBuilder(project, externalSystemId)
      .callback(callback)
      .use(progressExecutionMode)
      .withPreviewMode(isPreviewMode);
    refreshProject(externalProjectPath, builder);
  }

  /**
   * @deprecated use {@link ExternalSystemUtil#refreshProject(String, ImportSpec)} instead
   */
  @Deprecated
  public static void refreshProject(final @NotNull Project project,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull String externalProjectPath,
                                    final @NotNull ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    final @NotNull ProgressExecutionMode progressExecutionMode,
                                    final boolean reportRefreshError) {
    var builder = new ImportSpecBuilder(project, externalSystemId)
      .callback(callback)
      .use(progressExecutionMode)
      .withPreviewMode(isPreviewMode)
      .withActivateToolWindowOnFailure(reportRefreshError);
    refreshProject(externalProjectPath, builder);
  }

  public static void refreshProject(@NotNull String externalProjectPath, @NotNull ImportSpecBuilder importSpecBuilder) {
    refreshProject(externalProjectPath, importSpecBuilder.build());
  }

  public static void refreshProject(final @NotNull String externalProjectPath, final @NotNull ImportSpec importSpec) {
    refreshProjectImpl(Collections.singleton(externalProjectPath), importSpec);
  }

  private static void refreshProjectImpl(final @NotNull Set<String> externalProjectPaths, final @NotNull ImportSpec _importSpec) {
    var importSpec = new ImportSpecBuilder(_importSpec)
      .withPreviewMode(_importSpec.isPreviewMode() || !TrustedProjects.isProjectTrusted(_importSpec.getProject()))
      .build();

    var project = importSpec.getProject();
    var externalSystemId = importSpec.getExternalSystemId();
    var isPreviewMode = importSpec.isPreviewMode();
    var progressExecutionMode = importSpec.getProgressExecutionMode();

    if (progressExecutionMode == ProgressExecutionMode.NO_PROGRESS_SYNC ||
        progressExecutionMode == ProgressExecutionMode.NO_PROGRESS_ASYNC) {
      throw new IllegalArgumentException("Please, use progress for the project import!");
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("Stated " + externalSystemId + " load", new Throwable());
    }

    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    ApplicationManager.getApplication().invokeAndWait(FileDocumentManager.getInstance()::saveAllDocuments);

    ExternalSystemNotificationManager.getInstance(importSpec.getProject())
      .clearNotifications(null, NotificationSource.PROJECT_SYNC, importSpec.getExternalSystemId());

    AbstractExternalSystemLocalSettings<?> localSettings = ExternalSystemApiUtil.getLocalSettings(project, externalSystemId);

    for (var externalProjectPath : externalProjectPaths) {
      var projectSyncTypeStorage = localSettings.getProjectSyncType();
      var previousSyncType = projectSyncTypeStorage.get(externalProjectPath);
      var syncType = isPreviewMode ? PREVIEW : (previousSyncType == PREVIEW ? IMPORT : RE_IMPORT);
      projectSyncTypeStorage.put(externalProjectPath, syncType);

      var resolveProjectTask = new ExternalSystemResolveProjectTask(project, externalProjectPath, importSpec);

      var taskId = resolveProjectTask.getId();
      var projectName = resolveProjectTask.getProjectName();
      var externalSystemName = externalSystemId.getReadableName();
      var title = progressExecutionMode == ProgressExecutionMode.MODAL_SYNC
                  ? ExternalSystemBundle.message("progress.import.text", projectName, externalSystemName)
                  : ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemName);
      ExternalSystemTaskUnderProgress.executeTaskUnderProgress(project, title, progressExecutionMode, new ExternalSystemTaskUnderProgress() {

        @Override
        public @NotNull ExternalSystemTaskId getId() {
          return taskId;
        }

        @Override
        public void execute(@NotNull ProgressIndicator indicator) {
          var activity = ExternalSystemStatUtilKt.importActivityStarted(project, externalSystemId, null);
          try {
            ExternalSystemTelemetryUtil.runWithSpan(externalSystemId, "ExternalSystemSyncProjectTask", __ ->
              executeSync(externalProjectPath, importSpec, resolveProjectTask, indicator)
            );
          }
          finally {
            activity.finished();
          }
        }
      });
    }
  }

  private static void executeSync(
    @NotNull String externalProjectPath,
    @NotNull ImportSpec importSpec,
    @NotNull ExternalSystemResolveProjectTask resolveProjectTask,
    @NotNull ProgressIndicator indicator
  ) {
    var project = importSpec.getProject();
    var taskId = resolveProjectTask.getId();
    var externalSystemId = taskId.getProjectSystemId();
    var callback = importSpec.getCallback();
    var isPreviewMode = importSpec.isPreviewMode();

    if (project.isDisposed()) return;

    if (indicator instanceof ProgressIndicatorEx indicatorEx) {
      indicatorEx.addStateDelegate(new AbstractProgressIndicatorExBase() {
        @Override
        public void cancel() {
          super.cancel();
          resolveProjectTask.cancel();
        }
      });
    }

    var processingManager = ExternalSystemProcessingManager.getInstance();
    if (processingManager.findTask(ExternalSystemTaskType.RESOLVE_PROJECT, externalSystemId, externalProjectPath) != null) {
      if (callback != null) {
        callback.onFailure(taskId, ExternalSystemBundle.message("error.resolve.already.running", externalProjectPath), null);
      }
      return;
    }

    if (!isPreviewMode) {
      var externalSystemTaskActivator = ExternalProjectsManagerImpl.getInstance(project).getTaskActivator();
      if (!externalSystemTaskActivator.runTasks(externalProjectPath, ExternalSystemTaskActivator.Phase.BEFORE_SYNC)) {
        return;
      }
    }

    var projectName = resolveProjectTask.getProjectName();
    var processHandler = new ExternalSystemProcessHandler(resolveProjectTask, projectName + " import") {
      @Override
      protected void destroyProcessImpl() {
        resolveProjectTask.cancel();
        closeInput();
      }
    };

    var consoleManager = getConsoleManagerFor(resolveProjectTask);
    var consoleView = consoleManager.attachExecutionConsole(project, resolveProjectTask, null, processHandler);
    Disposer.register(project, Objects.requireNonNullElse(consoleView, processHandler));

    var syncViewManager = project.getService(SyncViewManager.class);
    try (BuildEventDispatcher eventDispatcher = new ExternalSystemEventDispatcher(taskId, syncViewManager, false)) {
      var finishSyncEventSupplier = new Ref<Supplier<? extends FinishBuildEvent>>();
      var taskListener = new ExternalSystemTaskNotificationListener() {

        @Override
        public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          if (isPreviewMode) return;
          var buildDescriptor = createSyncDescriptor(
            externalProjectPath, importSpec, resolveProjectTask, processHandler, consoleView, consoleManager
          );
          eventDispatcher.onEvent(id, new StartBuildEventImpl(buildDescriptor, BuildBundle.message("build.event.message.syncing")));
        }

        @Override
        public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) {
          processHandler.notifyTextAvailable(text, processOutputType);
          eventDispatcher.setStdOut(processOutputType.isStdout());
          eventDispatcher.append(text);
        }

        @Override
        public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) {
          finishSyncEventSupplier.set(() -> {
            var eventTime = System.currentTimeMillis();
            var eventMessage = BuildBundle.message("build.status.failed");
            var externalSystemName = externalSystemId.getReadableName();
            var title = ExternalSystemBundle.message("notification.project.refresh.fail.title", externalSystemName, projectName);
            var dataContext = BuildConsoleUtils.getDataContext(id, syncViewManager);
            var eventResult = createFailureResult(title, exception, externalSystemId, project, externalProjectPath, dataContext);
            return new FinishBuildEventImpl(id, null, eventTime, eventMessage, eventResult);
          });
          processHandler.notifyProcessTerminated(1);
        }

        @Override
        public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          finishSyncEventSupplier.set(() -> {
            var eventTime = System.currentTimeMillis();
            var eventMessage = BuildBundle.message("build.status.cancelled");
            var eventResult = new FailureResultImpl();
            return new FinishBuildEventImpl(id, null, eventTime, eventMessage, eventResult);
          });
          processHandler.notifyProcessTerminated(1);
        }

        @Override
        public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
          finishSyncEventSupplier.set(() -> {
            var eventTime = System.currentTimeMillis();
            var eventMessage = BuildBundle.message("build.status.finished");
            var eventResult = new SuccessResultImpl();
            return new FinishBuildEventImpl(id, null, eventTime, eventMessage, eventResult);
          });
          processHandler.notifyProcessTerminated(0);
        }

        @Override
        public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
          if (isPreviewMode) return;
          if (event instanceof ExternalSystemBuildEvent) {
            var buildEvent = ((ExternalSystemBuildEvent)event).getBuildEvent();
            eventDispatcher.onEvent(event.getId(), buildEvent);
          }
          else if (event instanceof ExternalSystemTaskExecutionEvent) {
            var buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
            eventDispatcher.onEvent(event.getId(), buildEvent);
          }
        }
      };

      incompleteDependenciesState(project, resolveProjectTask, () -> {
        LOG.info("External project [" + externalProjectPath + "] resolution task started");
        var startTS = System.currentTimeMillis();
        resolveProjectTask.execute(indicator, taskListener);
        var endTS = System.currentTimeMillis();
        LOG.info("External project [" + externalProjectPath + "] resolution task executed in " + (endTS - startTS) + " ms.");
        ExternalSystemTelemetryUtil.runWithSpan(externalSystemId, "ExternalSystemSyncResultProcessing",
                                                (ignore) -> handleSyncResult(externalProjectPath, importSpec, resolveProjectTask,
                                                                             eventDispatcher, finishSyncEventSupplier));
      });
    }
  }

  private static void incompleteDependenciesState(@NotNull Project project, @NotNull Object requestor, @NotNull Runnable runnable) {
    if (!Registry.is("external.system.incomplete.dependencies.state.during.sync")) {
      runnable.run();
    }
    var incompleteDependenciesService = project.getService(IncompleteDependenciesService.class);
    var incompleteDependenciesAccessToken = WriteAction.computeAndWait(() -> {
      return incompleteDependenciesService.enterIncompleteState(requestor);
    });
    try {
      runnable.run();
    }
    finally {
      WriteAction.runAndWait(() -> {
        incompleteDependenciesAccessToken.finish();
      });
    }
  }

  private static @NotNull DefaultBuildDescriptor createSyncDescriptor(
    @NotNull String externalProjectPath,
    @NotNull ImportSpec importSpec,
    @NotNull ExternalSystemResolveProjectTask resolveProjectTask,
    @NotNull ExternalSystemProcessHandler processHandler,
    @Nullable ExecutionConsole consoleView,
    @NotNull ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler> consoleManager
  ) {
    var project = importSpec.getProject();
    var taskId = resolveProjectTask.getId();
    var externalSystemId = taskId.getProjectSystemId();
    var rerunImportAction = new DumbAwareAction() {

      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(processHandler.isProcessTerminated());
      }

      @Override
      public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(false);
        Runnable rerunRunnable = importSpec.getRerunAction();
        if (rerunRunnable == null) {
          refreshProject(externalProjectPath, importSpec);
        }
        else {
          rerunRunnable.run();
        }
      }
    };
    var systemId = externalSystemId.getReadableName();
    rerunImportAction.getTemplatePresentation()
      .setText(ExternalSystemBundle.messagePointer("action.refresh.project.text", systemId));
    rerunImportAction.getTemplatePresentation()
      .setDescription(ExternalSystemBundle.messagePointer("action.refresh.project.description", systemId));
    rerunImportAction.getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);
    var projectName = resolveProjectTask.getProjectName();
    return new DefaultBuildDescriptor(taskId, projectName, externalProjectPath, System.currentTimeMillis())
      .withProcessHandler(processHandler, null)
      .withRestartAction(rerunImportAction)
      .withContentDescriptor(() -> {
        if (consoleView == null) return null;
        BuildContentDescriptor contentDescriptor = new BuildContentDescriptor(
          consoleView, processHandler, consoleView.getComponent(),
          ExternalSystemBundle.message("build.event.title.sync")
        );
        contentDescriptor.setActivateToolWindowWhenAdded(importSpec.isActivateBuildToolWindowOnStart());
        contentDescriptor.setActivateToolWindowWhenFailed(importSpec.isActivateBuildToolWindowOnFailure());
        contentDescriptor.setNavigateToError(importSpec.isNavigateToError());
        contentDescriptor.setAutoFocusContent(importSpec.isActivateBuildToolWindowOnFailure());
        return contentDescriptor;
      })
      .withActions(consoleManager.getCustomActions(project, resolveProjectTask, null))
      .withContextActions(consoleManager.getCustomContextActions(project, resolveProjectTask, null))
      .withExecutionFilters(consoleManager.getCustomExecutionFilters(project, resolveProjectTask, null));
  }

  private static void handleSyncResult(
    @NotNull String externalProjectPath,
    @NotNull ImportSpec importSpec,
    @NotNull ExternalSystemResolveProjectTask resolveProjectTask,
    @NotNull BuildEventDispatcher eventDispatcher,
    @NotNull Ref<Supplier<? extends FinishBuildEvent>> finishSyncEventSupplier
  ) {
    var project = importSpec.getProject();
    var taskId = resolveProjectTask.getId();
    var externalSystemId = taskId.getProjectSystemId();
    var isPreviewMode = importSpec.isPreviewMode();
    var callback = importSpec.getCallback();

    if (project.isDisposed()) return;

    try {
      var error = resolveProjectTask.getError();
      if (error == null) {
        var projectDataManager = ProjectDataManager.getInstance();
        var externalProjectData = projectDataManager.getExternalProjectData(project, externalSystemId, externalProjectPath);
        var externalProject = ObjectUtils.doIfNotNull(externalProjectData, it -> it.getExternalProjectStructure());
        if (externalProject != null) {
          if (importSpec.shouldCreateDirectoriesForEmptyContentRoots()) {
            externalProject.putUserData(ContentRootDataService.CREATE_EMPTY_DIRECTORIES, Boolean.TRUE);
          }
          if (importSpec.shouldImportProjectData()) {
            if (importSpec.shouldSelectProjectDataToImport()) {
              selectProjectDataToImport(project, externalProjectData);
            }
            projectDataManager.importData(externalProject, project);
          }
        }
        if (callback != null) {
          callback.onSuccess(taskId, externalProject);
        }
        if (!isPreviewMode) {
          var externalSystemTaskActivator = ExternalProjectsManagerImpl.getInstance(project).getTaskActivator();
          externalSystemTaskActivator.runTasks(externalProjectPath, ExternalSystemTaskActivator.Phase.AFTER_SYNC);
        }
        return;
      }
      if (error instanceof ImportCanceledException) {
        // stop refresh task
        return;
      }

      if (callback != null) {
        var message = ExternalSystemApiUtil.buildErrorMessage(error);
        if (StringUtil.isEmpty(message)) {
          var systemName = externalSystemId.getReadableName();
          message = String.format("Can't resolve %s project at '%s'. Reason: %s", systemName, externalProjectPath, message);
        }
        callback.onFailure(taskId, message, extractDetails(error));
      }
    }
    catch (Throwable t) {
      finishSyncEventSupplier.set(() -> {
        var eventTime = System.currentTimeMillis();
        var eventMessage = BuildBundle.message("build.status.failed");
        var systemName = externalSystemId.getReadableName();
        var projectName = resolveProjectTask.getProjectName();
        var title = ExternalSystemBundle.message("notification.project.refresh.fail.title", systemName, projectName);
        var eventResult = createFailureResult(title, t, externalSystemId, project, externalProjectPath, DataContext.EMPTY_CONTEXT);
        return new FinishBuildEventImpl(taskId, null, eventTime, eventMessage, eventResult);
      });
    }
    finally {
      if (!isPreviewMode) {
        if (isNewProject(project)) {
          var virtualFile = VfsUtil.findFileByIoFile(new File(externalProjectPath), false);
          if (virtualFile != null) {
            VfsUtil.markDirtyAndRefresh(true, false, true, virtualFile);
          }
        }
        project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, null);
        project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, null);
        project.putUserData(ExternalSystemDataKeys.NEWLY_OPENED_PROJECT_WITH_IDE_CACHES, null);
        eventDispatcher.onEvent(taskId, getSyncFinishEvent(taskId, finishSyncEventSupplier));
      }
    }
  }

  private static void selectProjectDataToImport(
    @NotNull Project project,
    @NotNull ExternalProjectInfo projectInfo
  ) {
    var application = ApplicationManager.getApplication();
    if (!application.isHeadlessEnvironment()) {
      application.invokeAndWait(() -> {
        var dialog = new ExternalProjectDataSelectorDialog(project, projectInfo);
        if (dialog.hasMultipleDataToSelect()) {
          dialog.showAndGet();
        }
        else {
          Disposer.dispose(dialog.getDisposable());
        }
      });
    }
  }

  private static @NotNull FinishBuildEvent getSyncFinishEvent(
    @NotNull ExternalSystemTaskId taskId,
    @NotNull Ref<? extends Supplier<? extends FinishBuildEvent>> finishSyncEventSupplier
  ) {
    Exception exception = null;
    var finishBuildEventSupplier = finishSyncEventSupplier.get();
    if (finishBuildEventSupplier != null) {
      try {
        return finishBuildEventSupplier.get();
      }
      catch (Exception e) {
        exception = e;
      }
    }
    if (!(exception instanceof ControlFlowException)) {
      LOG.warn("Sync finish event has not been received", exception);
    }
    var eventTime = System.currentTimeMillis();
    var eventMessage = BuildBundle.message("build.status.cancelled");
    var eventResult = new FailureResultImpl();
    return new FinishBuildEventImpl(taskId, null, eventTime, eventMessage, eventResult);
  }

  /**
   * @deprecated Use {@link ExternalSystemTrustedProjectDialog} instead
   */
  @Deprecated
  public static boolean confirmLoadingUntrustedProject(
    @NotNull Project project,
    @NotNull ProjectSystemId systemId
  ) {
    return ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(project, systemId);
  }

  /**
   * @deprecated Use {@link ExternalSystemTrustedProjectDialog} instead
   */
  @Deprecated(forRemoval = true)
  public static boolean confirmLoadingUntrustedProject(
    @NotNull Project project,
    @NotNull Collection<ProjectSystemId> systemIds
  ) {
    return ExternalSystemTrustedProjectDialog.confirmLoadingUntrustedProject(project, systemIds);
  }

  public static @NotNull @Nls String naturalJoinSystemIds(@NotNull Collection<ProjectSystemId> systemIds) {
    return new HashSet<>(systemIds).stream()
      .map(it -> it.getReadableName())
      .sorted(NaturalComparator.INSTANCE)
      .collect(NlsMessages.joiningAnd());
  }

  public static boolean isNewProject(Project project) {
    return project.getUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT) == Boolean.TRUE ||
           project.getUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT) == Boolean.TRUE;
  }

  // To be used only in internal New Project Wizard/Project Opening machinery
  @ApiStatus.Internal
  public static void configureNewModule(@NotNull Module module, boolean isCreatingNewProject, boolean isMavenModule) {
    var project = module.getProject();

    // Postpone project refresh, disable unwanted notifications
    project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, isCreatingNewProject ? Boolean.TRUE : null);
    project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, isCreatingNewProject ? Boolean.TRUE : null);

    markModuleAsMaven(module, null, isMavenModule);
  }

  // To be used only in internal New Project Wizard/Project Opening machinery
  @ApiStatus.Internal
  public static void markModuleAsMaven(@NotNull Module module, @Nullable String moduleVersion, boolean isMavenModule) {
    // This module will be replaced after import
    // Make sure the .iml file is not created under the project dir, if 'Store generated project files externally' setting is on.
    ExternalSystemModulePropertyManager.getInstance(module).setMavenized(isMavenModule, moduleVersion);
  }

  @ApiStatus.Internal
  public static @NotNull FailureResultImpl createFailureResult(
    @NotNull @Nls(capitalization = Sentence) String title,
    @NotNull Throwable exception,
    @NotNull ProjectSystemId externalSystemId,
    @NotNull Project project,
    @NotNull String externalProjectPath,
    @NotNull DataContext dataContext
  ) {
    var notificationManager = ExternalSystemNotificationManager.getInstance(project);
    var notificationData = createNotification(title, exception, externalSystemId, project, externalProjectPath, dataContext);
    if (notificationData == null) {
      return new FailureResultImpl();
    }
    return createFailureResult(exception, externalSystemId, project, notificationManager, notificationData);
  }

  private static @NotNull FailureResultImpl createFailureResult(
    @NotNull Throwable exception,
    @NotNull ProjectSystemId externalSystemId,
    @NotNull Project project,
    @NotNull ExternalSystemNotificationManager notificationManager,
    @NotNull NotificationData notificationData
  ) {
    if (notificationData.isBalloonNotification()) {
      notificationManager.showNotification(externalSystemId, notificationData);
      return new FailureResultImpl(exception);
    }

    final NotificationGroup group;
    if (notificationData.getBalloonGroup() == null) {
      var externalProjectsView = ExternalProjectsManagerImpl.getInstance(project)
        .getExternalProjectsView(externalSystemId);
      group = externalProjectsView instanceof ExternalProjectsViewImpl ?
              ((ExternalProjectsViewImpl)externalProjectsView).getNotificationGroup() : null;
    }
    else {
      group = notificationData.getBalloonGroup();
    }
    var line = notificationData.getLine() - 1;
    var column = notificationData.getColumn() - 1;
    var virtualFile = notificationData.getFilePath() != null
                      ? findLocalFileByPath(notificationData.getFilePath())
                      : null;

    var buildIssueNavigatable = exception instanceof BuildIssueException
                                ? ((BuildIssueException)exception).getBuildIssue().getNavigatable(project)
                                : null;
    final Navigatable navigatable;
    if (!isNullOrNonNavigatable(buildIssueNavigatable)) {
      navigatable = buildIssueNavigatable;
    }
    else if (isNullOrNonNavigatable(notificationData.getNavigatable())) {
      navigatable = virtualFile != null ? new OpenFileDescriptor(project, virtualFile, line, column) : NonNavigatable.INSTANCE;
    }
    else {
      navigatable = notificationData.getNavigatable();
    }

    final Notification notification;
    if (group == null) {
      notification = new Notification(externalSystemId.getReadableName() + " build", notificationData.getTitle(),
                                      notificationData.getMessage(),
                                      notificationData.getNotificationCategory().getNotificationType())
        .setListener(notificationData.getListener());
    }
    else {
      notification = group
        .createNotification(notificationData.getTitle(), notificationData.getMessage(), notificationData.getNotificationCategory().getNotificationType())
        .setListener(notificationData.getListener());
    }

    final FailureImpl failure;
    if (exception instanceof BuildIssueException) {
      var buildIssue = ((BuildIssueException)exception).getBuildIssue();
      failure = new FailureImpl(buildIssue.getTitle(), notificationData.getMessage(), Collections.emptyList(), exception, notification,
                                navigatable);
    } else {
      failure = new FailureImpl(notificationData.getMessage(), exception, notification, navigatable);
    }
    return new FailureResultImpl(Collections.singletonList(failure));
  }

  private static boolean isNullOrNonNavigatable(@Nullable Navigatable navigatable) {
    return navigatable == null || navigatable == NonNavigatable.INSTANCE;
  }

  public static @NotNull BuildEvent convert(@NotNull ExternalSystemTaskExecutionEvent event) {
    var buildEvent = ExternalSystemProgressEventConverter.convertBuildEvent(event);
    if (buildEvent == null) {
      // Migrated old fallback from previous implementation
      return new OutputBuildEventImpl(
        event.getProgressEvent().getEventId(),
        ObjectUtils.chooseNotNull(event.getProgressEvent().getParentEventId(), event.getId()),
        event.getProgressEvent().getDescriptor().getDisplayName(),
        true
      );
    }
    return buildEvent;
  }

  @ApiStatus.Obsolete
  public static void runTask(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             @NotNull String executorId,
                             @NotNull Project project,
                             @NotNull ProjectSystemId externalSystemId) {
    runTask(taskSettings, executorId, project, externalSystemId, null, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }

  @ApiStatus.Obsolete
  public static void runTask(final @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             final @NotNull String executorId,
                             final @NotNull Project project,
                             final @NotNull ProjectSystemId externalSystemId,
                             final @Nullable TaskCallback callback,
                             final @NotNull ProgressExecutionMode progressExecutionMode) {
    runTask(taskSettings, executorId, project, externalSystemId, callback, progressExecutionMode, true);
  }

  @ApiStatus.Obsolete
  public static void runTask(final @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             final @NotNull String executorId,
                             final @NotNull Project project,
                             final @NotNull ProjectSystemId externalSystemId,
                             final @Nullable TaskCallback callback,
                             final @NotNull ProgressExecutionMode progressExecutionMode,
                             boolean activateToolWindowBeforeRun) {
    runTask(taskSettings, executorId, project, externalSystemId, callback, progressExecutionMode, activateToolWindowBeforeRun, null);
  }

  @ApiStatus.Obsolete
  public static void runTask(final @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             final @NotNull String executorId,
                             final @NotNull Project project,
                             final @NotNull ProjectSystemId externalSystemId,
                             final @Nullable TaskCallback callback,
                             final @NotNull ProgressExecutionMode progressExecutionMode,
                             boolean activateToolWindowBeforeRun,
                             @Nullable UserDataHolderBase userData) {
    TaskExecutionSpec spec = TaskExecutionSpec.create()
      .withProject(project)
      .withSystemId(externalSystemId)
      .withExecutorId(executorId)
      .withSettings(taskSettings)
      .withProgressExecutionMode(progressExecutionMode)
      .withCallback(callback)
      .withUserData(userData)
      .withActivateToolWindowBeforeRun(activateToolWindowBeforeRun)
      .build();
    runTask(spec);
  }

  public static void runTask(@NotNull TaskExecutionSpec spec) {
    Project project = spec.getProject();
    ProjectSystemId externalSystemId = spec.getSystemId();

    var environment = createExecutionEnvironment(project, externalSystemId, spec.getSettings(), spec.getExecutorId());
    if (environment == null) {
      LOG.warn("Execution environment for " + externalSystemId + " is null");
      return;
    }

    var runnerAndConfigurationSettings = environment.getRunnerAndConfigurationSettings();
    assert runnerAndConfigurationSettings != null;
    runnerAndConfigurationSettings.setActivateToolWindowBeforeRun(spec.getActivateToolWindowBeforeRun());

    UserDataHolderBase userData = spec.getUserData();
    if (userData != null) {
      var runConfiguration = (ExternalSystemRunConfiguration)runnerAndConfigurationSettings.getConfiguration();
      userData.copyUserDataTo(runConfiguration);
    }

    var title = AbstractExternalSystemTaskConfigurationType.generateName(project, spec.getSettings());
    ExternalSystemTaskUnderProgress.executeTaskUnderProgress(project, title, spec.getProgressExecutionMode(),
                                                             new ExternalSystemTaskUnderProgress() {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        environment.putUserData(ExternalSystemRunnableState.PROGRESS_INDICATOR_KEY, indicator);
        environment.putUserData(ExternalSystemRunnableState.TASK_NOTIFICATION_LISTENER_KEY, spec.getListener());
        indicator.setIndeterminate(true);

        boolean result = waitForProcessExecution(project, environment, () -> environment.getRunner().execute(environment));
        TaskCallback callback = spec.getCallback();
        if (callback != null) {
          if (result) {
            callback.onSuccess();
          }
          else {
            callback.onFailure();
          }
        }
        if (!result && spec.getActivateToolWindowOnFailure()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            var window = ToolWindowManager.getInstance(project).getToolWindow(environment.getExecutor().getToolWindowId());
            if (window != null) {
              window.activate(null, false, false);
            }
          }, project.getDisposed());
        }
      }
    });
  }

  public static @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                                          @NotNull ProjectSystemId externalSystemId,
                                                                          @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                                                                          @NotNull String executorId) {
    var executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    if (executor == null) return null;

    var runnerId = getRunnerId(executorId);
    if (runnerId == null) return null;

    var runner = ProgramRunner.findRunnerById(runnerId);
    if (runner == null) return null;

    var settings = createExternalSystemRunnerAndConfigurationSettings(taskSettings, project, externalSystemId);
    if (settings == null) return null;

    return new ExecutionEnvironment(executor, runner, settings, project);
  }

  public static @Nullable RunnerAndConfigurationSettings createExternalSystemRunnerAndConfigurationSettings(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                                                                                                            @NotNull Project project,
                                                                                                            @NotNull ProjectSystemId externalSystemId) {
    var configurationType = findConfigurationType(externalSystemId);
    if (configurationType == null) {
      return null;
    }

    var name = AbstractExternalSystemTaskConfigurationType.generateName(project, taskSettings);
    var settings = RunManager.getInstance(project).createConfiguration(name, configurationType.getFactory());
    ((ExternalSystemRunConfiguration)settings.getConfiguration()).getSettings().setFrom(taskSettings);
    return settings;
  }

  public static @Nullable AbstractExternalSystemTaskConfigurationType findConfigurationType(@NotNull ProjectSystemId externalSystemId) {
    for (var type : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList()) {
      if (type instanceof AbstractExternalSystemTaskConfigurationType candidate) {
        if (externalSystemId.equals(candidate.getExternalSystemId())) {
          return candidate;
        }
      }
    }
    return null;
  }

  public static @Nullable String getRunnerId(@NotNull String executorId) {
    return RUNNER_IDS.get(executorId);
  }

  @SuppressWarnings("unused")
  public static void registerRunnerId(@NotNull String executorId, @NotNull String externalSystemRunnerId)  {
    if (!RUNNER_IDS.containsKey(executorId)) {
      RUNNER_IDS.put(executorId, externalSystemRunnerId);
    } else {
      throw new ExternalSystemException("Executor with ID " + executorId + " is already registered");
    }
  }

  /**
   * @deprecated use {@link ExternalSystemUtil#linkExternalProject(ExternalProjectSettings, ImportSpec)} instead
   */
  @Deprecated
  public static void linkExternalProject(
    final @NotNull ProjectSystemId externalSystemId,
    final @NotNull ExternalProjectSettings projectSettings,
    final @NotNull Project project,
    final @Nullable Consumer<? super Boolean> importResultCallback,
    boolean isPreviewMode,
    final @NotNull ProgressExecutionMode progressExecutionMode
  ) {
    ImportSpecBuilder builder = new ImportSpecBuilder(project, externalSystemId)
      .use(progressExecutionMode)
      .withPreviewMode(isPreviewMode)
      .withCallback(it -> {
        if (importResultCallback != null) {
          importResultCallback.accept(it);
        }
      });
    linkExternalProject(projectSettings, builder);
  }

  public static void linkExternalProject(
    @NotNull ExternalProjectSettings projectSettings,
    @NotNull ImportSpecBuilder importSpec
  ) {
    linkExternalProject(projectSettings, importSpec.build());
  }

  /**
   * Tries to obtain external project info implied by the given settings and link that external project to the given ide project.
   *
   * @param projectSettings settings of the external project to link
   * @param importSpec      defines the external project sync parameters
   */
  public static void linkExternalProject(
    @NotNull ExternalProjectSettings projectSettings,
    @NotNull ImportSpec importSpec
  ) {
    TrackingUtil.trackActivity(importSpec.getProject(), ExternalSystemActivityKey.INSTANCE, () -> {
      var systemSettings = ExternalSystemApiUtil.getSettings(importSpec.getProject(), importSpec.getExternalSystemId());
      var existingSettings = systemSettings.getLinkedProjectSettings(projectSettings.getExternalProjectPath());
      if (existingSettings != null) {
        return;
      }

      //noinspection unchecked
      systemSettings.linkProject(projectSettings);

      if (!Registry.is("external.system.auto.import.disabled")) {
        ExternalProjectsManager.getInstance(importSpec.getProject()).runWhenInitialized(() -> {
          refreshProject(projectSettings.getExternalProjectPath(), new ImportSpecBuilder(importSpec)
            .withSelectProjectDataToImport(systemSettings.showSelectiveImportDialogOnInitialImport())
          );
        });
      }
    });
  }

  public static @Nullable VirtualFile refreshAndFindFileByIoFile(final @NotNull File file) {
    var app = ApplicationManager.getApplication();
    if (!app.isDispatchThread()) {
      assert !app.holdsReadLock();
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.toPath());
  }

  public static @Nullable VirtualFile findLocalFileByPath(String path) {
    var application = ApplicationManager.getApplication();
    if (!application.isDispatchThread() && application.isReadAccessAllowed()) {
      // can not refresh under Read lock on non-dispatch thread. See VirtualFileSystem.refreshAndFindFileByPath javadoc
      return StandardFileSystems.local().findFileByPath(path);
    } else {
      return StandardFileSystems.local().refreshAndFindFileByPath(path);
    }
  }

  public static void scheduleExternalViewStructureUpdate(final @NotNull Project project, final @NotNull ProjectSystemId systemId) {
    var externalProjectsView = ExternalProjectsManagerImpl.getInstance(project).getExternalProjectsView(systemId);
    if (externalProjectsView instanceof ExternalProjectsViewImpl externalProjectsViewImpl) {
      externalProjectsViewImpl.scheduleStructureUpdate();
    }
  }

  /**
   * Get external project info containing custom data cache
   * for an external build system project of type projectSystemId at externalProjectPath
   * @param project IDEA project
   * @param projectSystemId external build system type id
   * @param externalProjectPath path to the external project
   * @return project info, or null if there is no such project, or project info cache is not yet ready
   * To wait for project info to become available, use
   * {@link com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager#runWhenInitialized(Runnable) ExternalProjectsManager#runWhenInitialized}
   */
  public static @Nullable ExternalProjectInfo getExternalProjectInfo(final @NotNull Project project,
                                                                     final @NotNull ProjectSystemId projectSystemId,
                                                                     final @NotNull String externalProjectPath) {
    var linkedProjectSettings = ExternalSystemApiUtil.getSettings(project, projectSystemId)
      .getLinkedProjectSettings(externalProjectPath);
    if (linkedProjectSettings == null) return null;

    return ProjectDataManagerImpl.getInstance()
      .getExternalProjectData(project, projectSystemId, linkedProjectSettings.getExternalProjectPath());
  }

  public static @NotNull ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler>
  getConsoleManagerFor(@NotNull ExternalSystemTask task) {
    for (ExternalSystemExecutionConsoleManager executionConsoleManager : ExternalSystemExecutionConsoleManager.EP_NAME.getExtensions()) {
      if (executionConsoleManager.isApplicableFor(task)) {
        //noinspection unchecked
        return executionConsoleManager;
      }
    }

    return new DefaultExternalSystemExecutionConsoleManager();
  }


  public static void invokeLater(Project p, Runnable r) {
    invokeLater(p, ModalityState.defaultModalityState(), r);
  }

  public static void invokeLater(final Project p, final ModalityState state, final Runnable r) {
    if (isNoBackgroundMode()) {
      r.run();
    }
    else {
      ApplicationManager.getApplication().invokeLater(r, state, p.getDisposed());
    }
  }

  public static boolean isNoBackgroundMode() {
    return (ApplicationManager.getApplication().isUnitTestMode()
            || ApplicationManager.getApplication().isHeadlessEnvironment() && !PlatformUtils.isFleetBackend());
  }

  @ApiStatus.Internal
  public static CompletableFuture<Void> requestImport(@NotNull Project project,
                                                      @NotNull String projectPath,
                                                      @NotNull ProjectSystemId systemId
  ) {
    var future = new CompletableFuture<Void>();
    var builder = new ImportSpecBuilder(project, systemId)
      .withCallback(future);
    refreshProject(projectPath, builder.build());
    return future;
  }

  @RequiresBackgroundThread
  private static boolean waitForProcessExecution(
    @NotNull Project project,
    @NotNull ExecutionEnvironment environment,
    @NotNull ThrowableRunnable<ExecutionException> runnable
  ) {
    try (var disposable = new AutoCloseableDisposable()) {
      var targetDone = new Semaphore();
      var result = new Ref<>(false);

      var executorId = environment.getExecutor().getId();
      project.getMessageBus().connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
        @Override
        public void processStartScheduled(@NotNull String executorIdLocal, @NotNull ExecutionEnvironment environmentLocal) {
          if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
            targetDone.down();
          }
        }

        @Override
        public void processNotStarted(@NotNull String executorIdLocal, @NotNull ExecutionEnvironment environmentLocal) {
          if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
            targetDone.up();
          }
        }

        @Override
        public void processTerminated(
          @NotNull String executorIdLocal,
          @NotNull ExecutionEnvironment environmentLocal,
          @NotNull ProcessHandler handler,
          int exitCode
        ) {
          if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
            result.set(exitCode == 0);
            targetDone.up();
          }
        }
      });
      try {
        ApplicationManager.getApplication().invokeAndWait(() -> {
          try {
            runnable.run();
          }
          catch (ExecutionException e) {
            targetDone.up();
            LOG.error(e);
          }
        }, ModalityState.defaultModalityState());
      }
      catch (Exception e) {
        targetDone.up();
        LOG.error(e);
      }
      targetDone.waitFor();
      return result.get();
    }
  }

  private static class AutoCloseableDisposable implements AutoCloseable, Disposable {

    @Override
    public void dispose() { }

    @Override
    public void close() {
      Disposer.dispose(this);
    }
  }
}
