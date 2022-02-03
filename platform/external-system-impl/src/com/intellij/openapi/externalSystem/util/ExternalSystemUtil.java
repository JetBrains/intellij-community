// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util;

import com.intellij.build.*;
import com.intellij.build.events.BuildEvent;
import com.intellij.build.events.EventResult;
import com.intellij.build.events.FinishBuildEvent;
import com.intellij.build.events.impl.FailureImpl;
import com.intellij.build.events.impl.FailureResultImpl;
import com.intellij.build.events.impl.SkippedResultImpl;
import com.intellij.build.events.impl.SuccessResultImpl;
import com.intellij.build.events.impl.*;
import com.intellij.build.issue.BuildIssue;
import com.intellij.execution.*;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.rmi.RemoteUtil;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.impl.TrustedProjects;
import com.intellij.ide.nls.NlsMessages;
import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.*;
import com.intellij.openapi.application.ex.ApplicationEx;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.execution.ExternalSystemExecutionConsoleManager;
import com.intellij.openapi.externalSystem.importing.ImportSpec;
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder;
import com.intellij.openapi.externalSystem.importing.ImportSpecImpl;
import com.intellij.openapi.externalSystem.issue.BuildIssueException;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.*;
import com.intellij.openapi.externalSystem.model.task.event.*;
import com.intellij.openapi.externalSystem.service.ImportCanceledException;
import com.intellij.openapi.externalSystem.service.execution.*;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager;
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemResolveProjectTask;
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemNotificationManager;
import com.intellij.openapi.externalSystem.service.notification.NotificationData;
import com.intellij.openapi.externalSystem.service.notification.NotificationSource;
import com.intellij.openapi.externalSystem.service.project.ExternalProjectRefreshCallback;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.project.manage.ContentRootDataService;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemTaskActivator;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemStatUtilKt;
import com.intellij.openapi.externalSystem.task.TaskCallback;
import com.intellij.openapi.externalSystem.view.ExternalProjectsView;
import com.intellij.openapi.externalSystem.view.ExternalProjectsViewImpl;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorExBase;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.NaturalComparator;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowEP;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.pom.Navigatable;
import com.intellij.pom.NonNavigatable;
import com.intellij.util.Consumer;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.ThreeState;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import static com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings.SyncType.*;
import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction;
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

      for (ToolWindowEP bean : ToolWindowEP.EP_NAME.getExtensionList()) {
        if (id.equals(bean.id)) {
          managerEx.initToolWindow(bean);
        }
      }
    }
    catch (Exception e) {
      LOG.error(String.format("Unable to initialize %s tool window", externalSystemId.getReadableName()), e);
    }
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
    ExternalSystemManager<?, ?, ?, ?, ?> manager = ExternalSystemApiUtil.getManager(spec.getExternalSystemId());
    if (manager == null) {
      return;
    }
    AbstractExternalSystemSettings<?, ?, ?> settings = manager.getSettingsProvider().fun(spec.getProject());
    final Collection<? extends ExternalProjectSettings> projectsSettings = settings.getLinkedProjectsSettings();
    if (projectsSettings.isEmpty()) {
      return;
    }

    final ExternalProjectRefreshCallback callback;
    if (spec.getCallback() == null) {
      callback = new MyMultiExternalProjectRefreshCallback(spec.getProject());
    }
    else {
      callback = spec.getCallback();
    }

    Set<String> toRefresh = new HashSet<>();
    for (ExternalProjectSettings setting : projectsSettings) {
      toRefresh.add(setting.getExternalProjectPath());
    }

    if (!toRefresh.isEmpty()) {
      ExternalSystemNotificationManager.getInstance(spec.getProject())
        .clearNotifications(null, NotificationSource.PROJECT_SYNC, spec.getExternalSystemId());

      for (String path : toRefresh) {
        refreshProject(path, new ImportSpecBuilder(spec).callback(callback));
      }
    }
  }

  private static @NotNull String extractDetails(@NotNull Throwable e) {
    Throwable unwrapped = RemoteUtil.unwrap(e);
    if (unwrapped instanceof ExternalSystemException) {
      return ((ExternalSystemException)unwrapped).getOriginalReason();
    }
    return ExternalSystemApiUtil.stacktraceAsString(e);
  }

  public static void refreshProject(final @NotNull Project project,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull String externalProjectPath,
                                    final boolean isPreviewMode,
                                    final @NotNull ProgressExecutionMode progressExecutionMode) {
    ImportSpecBuilder builder = new ImportSpecBuilder(project, externalSystemId).use(progressExecutionMode);
    if (isPreviewMode) builder.usePreviewMode();
    refreshProject(externalProjectPath, builder);
  }

  /**
   * <p>
   * Import external project.
   *
   * @param project             target intellij project to use
   * @param externalProjectPath path of the target external project's file
   * @param callback            callback to be notified on refresh result
   * @param isPreviewMode       flag that identifies whether libraries should be resolved during the refresh
   */
  public static void refreshProject(final @NotNull Project project,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull String externalProjectPath,
                                    final @NotNull ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    final @NotNull ProgressExecutionMode progressExecutionMode) {
    ImportSpecBuilder builder = new ImportSpecBuilder(project, externalSystemId).callback(callback).use(progressExecutionMode);
    if (isPreviewMode) builder.usePreviewMode();
    refreshProject(externalProjectPath, builder);
  }

  /**
   * <p>
   * Import external project.
   *
   * @param project             target intellij project to use
   * @param externalProjectPath path of the target external project
   * @param callback            callback to be notified on refresh result
   * @param isPreviewMode       flag that identifies whether libraries should be resolved during the refresh
   * @param reportRefreshError  prevent to show annoying error notification, e.g. if auto-import mode used
   */
  public static void refreshProject(final @NotNull Project project,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull String externalProjectPath,
                                    final @NotNull ExternalProjectRefreshCallback callback,
                                    final boolean isPreviewMode,
                                    final @NotNull ProgressExecutionMode progressExecutionMode,
                                    final boolean reportRefreshError) {
    ImportSpecBuilder builder = new ImportSpecBuilder(project, externalSystemId).callback(callback).use(progressExecutionMode);
    if (isPreviewMode) builder.usePreviewMode();
    if (!reportRefreshError) builder.dontReportRefreshErrors();
    refreshProject(externalProjectPath, builder);
  }

  public static void refreshProject(@NotNull String externalProjectPath, @NotNull ImportSpecBuilder importSpecBuilder) {
    refreshProject(externalProjectPath, importSpecBuilder.build());
  }

  public static void refreshProject(final @NotNull String externalProjectPath, final @NotNull ImportSpec importSpec) {
    Project project = importSpec.getProject();
    ProjectSystemId externalSystemId = importSpec.getExternalSystemId();
    ExternalProjectRefreshCallback callback = importSpec.getCallback();
    boolean isPreviewMode = importSpec.isPreviewMode();
    ProgressExecutionMode progressExecutionMode = importSpec.getProgressExecutionMode();
    boolean reportRefreshError = importSpec.isReportRefreshError();
    ThreeState isNavigateToError = importSpec.isNavigateToError();

    File projectFile = new File(externalProjectPath);
    final String projectName;
    if (projectFile.isFile()) {
      projectName = projectFile.getParentFile().getName();
    }
    else {
      projectName = projectFile.getName();
    }

    TransactionGuard.getInstance().assertWriteSafeContext(ModalityState.defaultModalityState());
    ApplicationManager.getApplication().invokeAndWait(FileDocumentManager.getInstance()::saveAllDocuments);

    if (!isPreviewMode && !TrustedProjects.isTrusted(project)) {
      LOG.debug("Skip " + externalSystemId + " load, because project is not trusted");
      return;
    }

    AbstractExternalSystemLocalSettings<?> localSettings = ExternalSystemApiUtil.getLocalSettings(project, externalSystemId);
    AbstractExternalSystemLocalSettings.SyncType syncType =
      isPreviewMode ? PREVIEW :
      localSettings.getProjectSyncType().get(externalProjectPath) == PREVIEW ? IMPORT : RE_IMPORT;
    localSettings.getProjectSyncType().put(externalProjectPath, syncType);

    ExternalSystemResolveProjectTask resolveProjectTask = new ExternalSystemResolveProjectTask(project, externalProjectPath, importSpec);

    final TaskUnderProgress refreshProjectStructureTask = new TaskUnderProgress() {

      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        String title = ExternalSystemBundle.message("progress.refresh.text", projectName, externalSystemId.getReadableName());
        StructuredIdeActivity activity = ExternalSystemStatUtilKt.importActivityStarted(project, externalSystemId, null);
        try {
          DumbService.getInstance(project).suspendIndexingAndRun(title, () -> executeImpl(indicator));
        }
        finally {
          activity.finished();
        }
      }

      private void executeImpl(@NotNull ProgressIndicator indicator) {
        if (project.isDisposed()) return;

        if (indicator instanceof ProgressIndicatorEx) {
          ((ProgressIndicatorEx)indicator).addStateDelegate(new AbstractProgressIndicatorExBase() {
            @Override
            public void cancel() {
              super.cancel();
              cancelImport();
            }
          });
        }

        ExternalSystemProcessingManager processingManager =
          ApplicationManager.getApplication().getService(ExternalSystemProcessingManager.class);
        if (processingManager.findTask(ExternalSystemTaskType.RESOLVE_PROJECT, externalSystemId, externalProjectPath) != null) {
          if (callback != null) {
            callback
              .onFailure(resolveProjectTask.getId(), ExternalSystemBundle.message("error.resolve.already.running", externalProjectPath),
                         null);
          }
          return;
        }

        if (!(callback instanceof MyMultiExternalProjectRefreshCallback)) {
          ExternalSystemNotificationManager.getInstance(project)
            .clearNotifications(null, NotificationSource.PROJECT_SYNC, externalSystemId);
        }

        final ExternalSystemTaskActivator externalSystemTaskActivator = ExternalProjectsManagerImpl.getInstance(project).getTaskActivator();
        if (!isPreviewMode && !externalSystemTaskActivator.runTasks(externalProjectPath, ExternalSystemTaskActivator.Phase.BEFORE_SYNC)) {
          return;
        }

        final ExternalSystemProcessHandler processHandler = new ExternalSystemProcessHandler(resolveProjectTask, projectName + " import") {
          @Override
          protected void destroyProcessImpl() {
            cancelImport();
            closeInput();
          }
        };

        final ExternalSystemExecutionConsoleManager<ExecutionConsole, ProcessHandler>
          consoleManager = getConsoleManagerFor(resolveProjectTask);

        final ExecutionConsole consoleView =
          consoleManager.attachExecutionConsole(project, resolveProjectTask, null, processHandler);
        Disposer.register(project, Objects.requireNonNullElse(consoleView, processHandler));

        Ref<Supplier<? extends FinishBuildEvent>> finishSyncEventSupplier = Ref.create();
        SyncViewManager syncViewManager = project.getService(SyncViewManager.class);
        try (BuildEventDispatcher eventDispatcher = new ExternalSystemEventDispatcher(resolveProjectTask.getId(), syncViewManager, false)) {
          ExternalSystemTaskNotificationListenerAdapter taskListener = new ExternalSystemTaskNotificationListenerAdapter() {
            @Override
            public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
              long eventTime = System.currentTimeMillis();
              AnAction rerunImportAction = new DumbAwareAction() {
                @Override
                public void update(@NotNull AnActionEvent e) {
                  Presentation p = e.getPresentation();
                  p.setEnabled(processHandler.isProcessTerminated());
                }

                @Override
                public void actionPerformed(@NotNull AnActionEvent e) {
                  Presentation p = e.getPresentation();
                  p.setEnabled(false);
                  Runnable rerunRunnable = importSpec instanceof ImportSpecImpl ? ((ImportSpecImpl)importSpec).getRerunAction() : null;
                  if (rerunRunnable == null) {
                    refreshProject(externalProjectPath, importSpec);
                  }
                  else {
                    rerunRunnable.run();
                  }
                }
              };
              String systemId = id.getProjectSystemId().getReadableName();
              rerunImportAction.getTemplatePresentation()
                .setText(ExternalSystemBundle.messagePointer("action.refresh.project.text", systemId));
              rerunImportAction.getTemplatePresentation()
                .setDescription(ExternalSystemBundle.messagePointer("action.refresh.project.description", systemId));
              rerunImportAction.getTemplatePresentation().setIcon(AllIcons.Actions.Refresh);

              if (isPreviewMode) return;
              DefaultBuildDescriptor buildDescriptor = new DefaultBuildDescriptor(id, projectName, externalProjectPath, eventTime)
                .withProcessHandler(processHandler, null)
                .withRestartAction(rerunImportAction)
                .withContentDescriptor(() -> {
                  if (consoleView == null) return null;
                  boolean activateToolWindow = isNewProject(project);
                  BuildContentDescriptor contentDescriptor =
                    new BuildContentDescriptor(consoleView, processHandler, consoleView.getComponent(),
                                               ExternalSystemBundle.message("build.event.title.sync"));
                  contentDescriptor.setActivateToolWindowWhenAdded(activateToolWindow);
                  contentDescriptor.setActivateToolWindowWhenFailed(reportRefreshError);
                  contentDescriptor.setNavigateToError(isNavigateToError);
                  contentDescriptor.setAutoFocusContent(reportRefreshError);
                  return contentDescriptor;
                })
                .withActions(consoleManager.getCustomActions(project, resolveProjectTask, null))
                .withContextActions(consoleManager.getCustomContextActions(project, resolveProjectTask, null));
              Filter[] filters = consoleManager.getCustomExecutionFilters(project, resolveProjectTask, null);
              Arrays.stream(filters).forEach(buildDescriptor::withExecutionFilter);
              eventDispatcher.onEvent(id, new StartBuildEventImpl(buildDescriptor, BuildBundle.message("build.event.message.syncing")));
            }

            @Override
            public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
              processHandler.notifyTextAvailable(text, stdOut ? ProcessOutputTypes.STDOUT : ProcessOutputTypes.STDERR);
              eventDispatcher.setStdOut(stdOut);
              eventDispatcher.append(text);
            }

            @Override
            public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
              String title = ExternalSystemBundle.message("notification.project.refresh.fail.title",
                                                          externalSystemId.getReadableName(), projectName);
              DataContext dataContext = BuildConsoleUtils.getDataContext(id, syncViewManager);
              com.intellij.build.events.FailureResult failureResult =
                createFailureResult(title, e, externalSystemId, project, dataContext);
              finishSyncEventSupplier.set(() -> new FinishBuildEventImpl(id, null, System.currentTimeMillis(),
                                                                         BuildBundle.message("build.status.failed"), failureResult));
              processHandler.notifyProcessTerminated(1);
            }

            @Override
            public void onCancel(@NotNull ExternalSystemTaskId id) {
              finishSyncEventSupplier.set(() -> new FinishBuildEventImpl(id, null, System.currentTimeMillis(),
                                                                         BuildBundle.message("build.status.cancelled"),
                                                                         new FailureResultImpl()));
              processHandler.notifyProcessTerminated(1);
            }

            @Override
            public void onSuccess(@NotNull ExternalSystemTaskId id) {
              finishSyncEventSupplier.set(
                () -> new FinishBuildEventImpl(id, null, System.currentTimeMillis(), BuildBundle.message("build.status.finished"),
                                               new SuccessResultImpl()));
              processHandler.notifyProcessTerminated(0);
            }

            @Override
            public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
              if (isPreviewMode) return;
              if (event instanceof ExternalSystemBuildEvent) {
                BuildEvent buildEvent = ((ExternalSystemBuildEvent)event).getBuildEvent();
                eventDispatcher.onEvent(event.getId(), buildEvent);
              }
              else if (event instanceof ExternalSystemTaskExecutionEvent) {
                BuildEvent buildEvent = convert(((ExternalSystemTaskExecutionEvent)event));
                eventDispatcher.onEvent(event.getId(), buildEvent);
              }
            }
          };

          LOG.info("External project [" + externalProjectPath + "] resolution task started");
          final long startTS = System.currentTimeMillis();
          resolveProjectTask.execute(indicator, taskListener);
          LOG.info("External project [" + externalProjectPath + "] resolution task executed in " +
                   (System.currentTimeMillis() - startTS) + " ms.");
          handExecutionResult(externalSystemTaskActivator, eventDispatcher, finishSyncEventSupplier);
        }
      }

      private void handExecutionResult(@NotNull ExternalSystemTaskActivator externalSystemTaskActivator,
                                       @NotNull BuildEventDispatcher eventDispatcher,
                                       @NotNull Ref<? extends Supplier<? extends FinishBuildEvent>> finishSyncEventSupplier) {
        if (project.isDisposed()) return;

        try {
          final Throwable error = resolveProjectTask.getError();
          if (error == null) {
            if (callback != null) {
              final ExternalProjectInfo externalProjectData = ProjectDataManagerImpl.getInstance()
                .getExternalProjectData(project, externalSystemId, externalProjectPath);
              if (externalProjectData != null) {
                DataNode<ProjectData> externalProject = externalProjectData.getExternalProjectStructure();
                if (externalProject != null && importSpec.shouldCreateDirectoriesForEmptyContentRoots()) {
                  externalProject.putUserData(ContentRootDataService.CREATE_EMPTY_DIRECTORIES, Boolean.TRUE);
                }
                callback.onSuccess(resolveProjectTask.getId(), externalProject);
              }
            }
            if (!isPreviewMode) {
              externalSystemTaskActivator.runTasks(externalProjectPath, ExternalSystemTaskActivator.Phase.AFTER_SYNC);
            }
            return;
          }
          if (error instanceof ImportCanceledException) {
            // stop refresh task
            return;
          }
          String message = ExternalSystemApiUtil.buildErrorMessage(error);
          if (StringUtil.isEmpty(message)) {
            message = String.format(
              "Can't resolve %s project at '%s'. Reason: %s", externalSystemId.getReadableName(), externalProjectPath, message
            );
          }

          if (callback != null) {
            callback.onFailure(resolveProjectTask.getId(), message, extractDetails(error));
          }
        }
        finally {
          if (!isPreviewMode) {
            boolean isNewProject = isNewProject(project);
            if (isNewProject) {
              VirtualFile virtualFile = VfsUtil.findFileByIoFile(projectFile, false);
              if (virtualFile != null) {
                VfsUtil.markDirtyAndRefresh(true, false, true, virtualFile);
              }
            }
            project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, null);
            project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, null);
            eventDispatcher.onEvent(resolveProjectTask.getId(), getSyncFinishEvent(finishSyncEventSupplier));
          }
        }
      }

      private @NotNull FinishBuildEvent getSyncFinishEvent(@NotNull Ref<? extends Supplier<? extends FinishBuildEvent>> finishSyncEventSupplier) {
        Exception exception = null;
        Supplier<? extends FinishBuildEvent> finishBuildEventSupplier = finishSyncEventSupplier.get();
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
        return new FinishBuildEventImpl(resolveProjectTask.getId(), null, System.currentTimeMillis(),
                                        BuildBundle.message("build.status.cancelled"), new FailureResultImpl());
      }

      private void cancelImport() {
        resolveProjectTask.cancel();
      }
    };

    final String title;
    switch (progressExecutionMode) {
      case NO_PROGRESS_SYNC:
      case NO_PROGRESS_ASYNC:
        throw new ExternalSystemException("Please, use progress for the project import!");
      case MODAL_SYNC:
        title = ExternalSystemBundle.message("progress.import.text", projectName, externalSystemId.getReadableName());
        new Task.Modal(project, title, true) {
          @Override
          public @NotNull Object getId() {
            return resolveProjectTask.getId();
          }

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
          public @NotNull Object getId() {
            return resolveProjectTask.getId();
          }

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
          public @NotNull Object getId() {
            return resolveProjectTask.getId();
          }

          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            refreshProjectStructureTask.execute(indicator);
          }
        }.queue();
    }
  }

  public static boolean confirmLoadingUntrustedProject(
    @NotNull Project project,
    @NotNull ProjectSystemId systemId
  ) {
    return confirmLoadingUntrustedProject(project, Collections.singletonList(systemId));
  }

  public static boolean confirmLoadingUntrustedProject(
    @NotNull Project project,
    @NotNull Collection<ProjectSystemId> systemIds
  ) {
    String systemsPresentation = naturalJoinSystemIds(systemIds);
    return TrustedProjects.isTrusted(project) ||
           TrustedProjects.confirmLoadingUntrustedProject(
             project,
             IdeBundle.message("untrusted.project.dialog.title", systemsPresentation, systemIds.size()),
             IdeBundle.message("untrusted.project.dialog.text", systemsPresentation, systemIds.size()),
             IdeBundle.message("untrusted.project.dialog.trust.button"),
             IdeBundle.message("untrusted.project.dialog.distrust.button")
           );
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

  @ApiStatus.Internal
  public static @NotNull FailureResultImpl createFailureResult(@NotNull @Nls(capitalization = Sentence) String title,
                                                               @NotNull Exception exception,
                                                               @NotNull ProjectSystemId externalSystemId,
                                                               @NotNull Project project,
                                                               @NotNull DataContext dataContext) {
    ExternalSystemNotificationManager notificationManager = ExternalSystemNotificationManager.getInstance(project);
    NotificationData notificationData =
      notificationManager.createNotification(title, exception, externalSystemId, project, dataContext);
    if (notificationData == null) {
      return new FailureResultImpl();
    }
    return createFailureResult(exception, externalSystemId, project, notificationManager, notificationData);
  }

  private static @NotNull FailureResultImpl createFailureResult(@NotNull Exception exception,
                                                                @NotNull ProjectSystemId externalSystemId,
                                                                @NotNull Project project,
                                                                @NotNull ExternalSystemNotificationManager notificationManager,
                                                                @NotNull NotificationData notificationData) {
    if (notificationData.isBalloonNotification()) {
      notificationManager.showNotification(externalSystemId, notificationData);
      return new FailureResultImpl(exception);
    }

    NotificationGroup group;
    if (notificationData.getBalloonGroup() == null) {
      ExternalProjectsView externalProjectsView =
        ExternalProjectsManagerImpl.getInstance(project).getExternalProjectsView(externalSystemId);
      group = externalProjectsView instanceof ExternalProjectsViewImpl ?
              ((ExternalProjectsViewImpl)externalProjectsView).getNotificationGroup() : null;
    }
    else {
      group = notificationData.getBalloonGroup();
    }
    int line = notificationData.getLine() - 1;
    int column = notificationData.getColumn() - 1;
    final VirtualFile virtualFile =
      notificationData.getFilePath() != null ? findLocalFileByPath(notificationData.getFilePath()) : null;

    final Navigatable navigatable;
    Navigatable buildIssueNavigatable =
      exception instanceof BuildIssueException ? ((BuildIssueException)exception).getBuildIssue().getNavigatable(project) : null;
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
    FailureImpl failure;
    if (exception instanceof BuildIssueException) {
      BuildIssue buildIssue = ((BuildIssueException)exception).getBuildIssue();
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

  public static BuildEvent convert(ExternalSystemTaskExecutionEvent taskExecutionEvent) {
    ExternalSystemProgressEvent progressEvent = taskExecutionEvent.getProgressEvent();
    String displayName = progressEvent.getDescriptor().getDisplayName();
    long eventTime = progressEvent.getDescriptor().getEventTime();
    Object parentEventId = ObjectUtils.chooseNotNull(progressEvent.getParentEventId(), taskExecutionEvent.getId());

    AbstractBuildEvent buildEvent;
    if (progressEvent instanceof ExternalSystemStartEvent) {
      buildEvent = new StartEventImpl(progressEvent.getEventId(), parentEventId, eventTime, displayName);
    }
    else if (progressEvent instanceof ExternalSystemFinishEvent) {
      final EventResult eventResult;
      final OperationResult operationResult = ((ExternalSystemFinishEvent<?>)progressEvent).getOperationResult();
      if (operationResult instanceof FailureResult) {
        List<com.intellij.build.events.Failure> failures = new SmartList<>();
        for (Failure failure : ((FailureResult)operationResult).getFailures()) {
          failures.add(convert(failure));
        }
        eventResult = new FailureResultImpl(failures);
      }
      else if (operationResult instanceof SkippedResult) {
        eventResult = new SkippedResultImpl();
      }
      else if (operationResult instanceof SuccessResult) {
        eventResult = new SuccessResultImpl(((SuccessResult)operationResult).isUpToDate());
      }
      else {
        eventResult = new SuccessResultImpl();
      }
      buildEvent = new FinishEventImpl(progressEvent.getEventId(), parentEventId, eventTime, displayName, eventResult);
    }
    else if (progressEvent instanceof ExternalSystemStatusEvent) {
      ExternalSystemStatusEvent statusEvent = (ExternalSystemStatusEvent)progressEvent;
      buildEvent = new ProgressBuildEventImpl(progressEvent.getEventId(), progressEvent.getParentEventId(), eventTime, displayName,
                                              statusEvent.getTotal(), statusEvent.getProgress(), statusEvent.getUnit());
    }
    else {
      buildEvent = new OutputBuildEventImpl(progressEvent.getEventId(), parentEventId, displayName, true);
    }

    String hint = progressEvent.getDescriptor().getHint();
    buildEvent.setHint(hint);
    return buildEvent;
  }

  private static com.intellij.build.events.Failure convert(Failure failure) {
    List<com.intellij.build.events.Failure> causes = new SmartList<>();
    for (Failure cause : failure.getCauses()) {
      causes.add(convert(cause));
    }
    return new FailureImpl(failure.getMessage(), failure.getDescription(), causes);
  }

  public static void runTask(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             @NotNull String executorId,
                             @NotNull Project project,
                             @NotNull ProjectSystemId externalSystemId) {
    runTask(taskSettings, executorId, project, externalSystemId, null, ProgressExecutionMode.IN_BACKGROUND_ASYNC);
  }

  public static void runTask(final @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             final @NotNull String executorId,
                             final @NotNull Project project,
                             final @NotNull ProjectSystemId externalSystemId,
                             final @Nullable TaskCallback callback,
                             final @NotNull ProgressExecutionMode progressExecutionMode) {
    runTask(taskSettings, executorId, project, externalSystemId, callback, progressExecutionMode, true);
  }

  public static void runTask(final @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             final @NotNull String executorId,
                             final @NotNull Project project,
                             final @NotNull ProjectSystemId externalSystemId,
                             final @Nullable TaskCallback callback,
                             final @NotNull ProgressExecutionMode progressExecutionMode,
                             boolean activateToolWindowBeforeRun) {
    runTask(taskSettings, executorId, project, externalSystemId, callback, progressExecutionMode, activateToolWindowBeforeRun, null);
  }

  public static void runTask(final @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                             final @NotNull String executorId,
                             final @NotNull Project project,
                             final @NotNull ProjectSystemId externalSystemId,
                             final @Nullable TaskCallback callback,
                             final @NotNull ProgressExecutionMode progressExecutionMode,
                             boolean activateToolWindowBeforeRun,
                             @Nullable UserDataHolderBase userData) {
    ExecutionEnvironment environment = createExecutionEnvironment(project, externalSystemId, taskSettings, executorId);
    if (environment == null) {
      LOG.warn("Execution environment for " + externalSystemId + " is null");
      return;
    }

    RunnerAndConfigurationSettings runnerAndConfigurationSettings = environment.getRunnerAndConfigurationSettings();
    assert runnerAndConfigurationSettings != null;
    runnerAndConfigurationSettings.setActivateToolWindowBeforeRun(activateToolWindowBeforeRun);

    if (userData != null) {
      ExternalSystemRunConfiguration runConfiguration = (ExternalSystemRunConfiguration)runnerAndConfigurationSettings.getConfiguration();
      userData.copyUserDataTo(runConfiguration);
    }

    final TaskUnderProgress task = new TaskUnderProgress() {
      @Override
      public void execute(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);
        final Semaphore targetDone = new Semaphore();
        final Ref<Boolean> result = new Ref<>(false);
        final Disposable disposable = Disposer.newDisposable();

        project.getMessageBus().connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, new ExecutionListener() {
          @Override
          public void processStartScheduled(final @NotNull String executorIdLocal, final @NotNull ExecutionEnvironment environmentLocal) {
            if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
              targetDone.down();
            }
          }

          @Override
          public void processNotStarted(final @NotNull String executorIdLocal, final @NotNull ExecutionEnvironment environmentLocal) {
            if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
              targetDone.up();
            }
          }

          @Override
          public void processTerminated(@NotNull String executorIdLocal,
                                        @NotNull ExecutionEnvironment environmentLocal,
                                        @NotNull ProcessHandler handler,
                                        int exitCode) {
            if (executorId.equals(executorIdLocal) && environment.equals(environmentLocal)) {
              result.set(exitCode == 0);
              targetDone.up();
            }
          }
        });

        try {
          ApplicationManager.getApplication().invokeAndWait(() -> {
            try {
              environment.getRunner().execute(environment);
            }
            catch (ExecutionException e) {
              targetDone.up();
              LOG.error(e);
            }
          }, ModalityState.defaultModalityState());
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
        if (!result.get()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            ToolWindow window = ToolWindowManager.getInstance(project).getToolWindow(environment.getExecutor().getToolWindowId());
            if (window != null) {
              window.activate(null, false, false);
            }
          }, project.getDisposed());
        }
      }
    };

    final String title = AbstractExternalSystemTaskConfigurationType.generateName(project, taskSettings);
    switch (progressExecutionMode) {
      case NO_PROGRESS_SYNC:
        task.execute(new EmptyProgressIndicator());
        break;
      case MODAL_SYNC:
        new Task.Modal(project, title, true) {
          @Override
          public void run(@NotNull ProgressIndicator indicator) {
            task.execute(indicator);
          }
        }.queue();
        break;
      case NO_PROGRESS_ASYNC:
        ApplicationManager.getApplication().executeOnPooledThread(() -> task.execute(new EmptyProgressIndicator()));
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

  public static @Nullable ExecutionEnvironment createExecutionEnvironment(@NotNull Project project,
                                                                          @NotNull ProjectSystemId externalSystemId,
                                                                          @NotNull ExternalSystemTaskExecutionSettings taskSettings,
                                                                          @NotNull String executorId) {
    Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
    if (executor == null) return null;

    String runnerId = getRunnerId(executorId);
    if (runnerId == null) return null;

    ProgramRunner runner = ProgramRunner.findRunnerById(runnerId);
    if (runner == null) return null;

    RunnerAndConfigurationSettings settings = createExternalSystemRunnerAndConfigurationSettings(taskSettings, project, externalSystemId);
    if (settings == null) return null;

    return new ExecutionEnvironment(executor, runner, settings, project);
  }

  public static @Nullable RunnerAndConfigurationSettings createExternalSystemRunnerAndConfigurationSettings(@NotNull ExternalSystemTaskExecutionSettings taskSettings,
                                                                                                            @NotNull Project project,
                                                                                                            @NotNull ProjectSystemId externalSystemId) {
    AbstractExternalSystemTaskConfigurationType configurationType = findConfigurationType(externalSystemId);
    if (configurationType == null) {
      return null;
    }

    String name = AbstractExternalSystemTaskConfigurationType.generateName(project, taskSettings);
    RunnerAndConfigurationSettings settings = RunManager.getInstance(project).createConfiguration(name, configurationType.getFactory());
    ((ExternalSystemRunConfiguration)settings.getConfiguration()).getSettings().setFrom(taskSettings);
    return settings;
  }

  public static @Nullable AbstractExternalSystemTaskConfigurationType findConfigurationType(@NotNull ProjectSystemId externalSystemId) {
    for (ConfigurationType type : ConfigurationType.CONFIGURATION_TYPE_EP.getExtensionList()) {
      if (type instanceof AbstractExternalSystemTaskConfigurationType) {
        AbstractExternalSystemTaskConfigurationType candidate = (AbstractExternalSystemTaskConfigurationType)type;
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

  /**
   * Tries to obtain external project info implied by the given settings and link that external project to the given ide project.
   *
   * @param externalSystemId      target external system
   * @param projectSettings       settings of the external project to link
   * @param project               target ide project to link external project to
   * @param importResultCallback  it might take a while to resolve external project info, that's why it's possible to provide
   *                              a callback to be notified on processing result. It receives {@code true} if an external
   *                              project info has been successfully obtained, {@code false} otherwise.
   * @param isPreviewMode         flag which identifies if missing external project binaries should be downloaded
   * @param progressExecutionMode identifies how progress bar will be represented for the current processing
   */
  public static void linkExternalProject(final @NotNull ProjectSystemId externalSystemId,
                                         final @NotNull ExternalProjectSettings projectSettings,
                                         final @NotNull Project project,
                                         final @Nullable Consumer<? super Boolean> importResultCallback,
                                         boolean isPreviewMode,
                                         final @NotNull ProgressExecutionMode progressExecutionMode) {
    AbstractExternalSystemSettings systemSettings = ExternalSystemApiUtil.getSettings(project, externalSystemId);
    ExternalProjectSettings existingSettings = systemSettings.getLinkedProjectSettings(projectSettings.getExternalProjectPath());
    if (existingSettings != null) {
      return;
    }

    //noinspection unchecked
    systemSettings.linkProject(projectSettings);
    ensureToolWindowInitialized(project, externalSystemId);
    ExternalProjectRefreshCallback callback = new ExternalProjectRefreshCallback() {
      @Override
      public void onSuccess(final @Nullable DataNode<ProjectData> externalProject) {
        if (externalProject == null) {
          if (importResultCallback != null) {
            importResultCallback.consume(false);
          }
          return;
        }
        ApplicationManager.getApplication().getService(ProjectDataManager.class).importData(externalProject, project, true);
        if (importResultCallback != null) {
          importResultCallback.consume(true);
        }
      }

      @Override
      public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
        if (importResultCallback != null) {
          importResultCallback.consume(false);
        }
      }
    };

    ImportSpecBuilder importSpecBuilder = new ImportSpecBuilder(project, externalSystemId).callback(callback).use(progressExecutionMode);
    if (isPreviewMode) importSpecBuilder.usePreviewMode();
    refreshProject(projectSettings.getExternalProjectPath(), importSpecBuilder);
  }

  public static @Nullable VirtualFile refreshAndFindFileByIoFile(final @NotNull File file) {
    final Application app = ApplicationManager.getApplication();
    if (!app.isDispatchThread()) {
      assert !((ApplicationEx)app).holdsReadLock();
    }
    return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file.toPath());
  }

  public static @Nullable VirtualFile findLocalFileByPath(String path) {
    return ApplicationManager.getApplication().isReadAccessAllowed()
           ? findLocalFileByPathUnderReadAction(path)
           : findLocalFileByPathUnderWriteAction(path);
  }

  private static @Nullable VirtualFile findLocalFileByPathUnderWriteAction(final String path) {
    return doWriteAction(() -> StandardFileSystems.local().refreshAndFindFileByPath(path));
  }

  private static @Nullable VirtualFile findLocalFileByPathUnderReadAction(final String path) {
    return ReadAction.compute(() -> StandardFileSystems.local().findFileByPath(path));
  }

  public static void scheduleExternalViewStructureUpdate(final @NotNull Project project, final @NotNull ProjectSystemId systemId) {
    ExternalProjectsView externalProjectsView = ExternalProjectsManagerImpl.getInstance(project).getExternalProjectsView(systemId);
    if (externalProjectsView instanceof ExternalProjectsViewImpl) {
      ((ExternalProjectsViewImpl)externalProjectsView).scheduleStructureUpdate();
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
    final ExternalProjectSettings linkedProjectSettings =
      ExternalSystemApiUtil.getSettings(project, projectSystemId).getLinkedProjectSettings(externalProjectPath);
    if (linkedProjectSettings == null) return null;

    return ProjectDataManagerImpl.getInstance().getExternalProjectData(
      project, projectSystemId, linkedProjectSettings.getExternalProjectPath());
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
            || ApplicationManager.getApplication().isHeadlessEnvironment());
  }

  private interface TaskUnderProgress {
    void execute(@NotNull ProgressIndicator indicator);
  }

  private static final class MyMultiExternalProjectRefreshCallback implements ExternalProjectRefreshCallback {
    private final Project myProject;

    MyMultiExternalProjectRefreshCallback(Project project) {
      myProject = project;
    }

    @Override
    public void onSuccess(final @Nullable DataNode<ProjectData> externalProject) {
      if (externalProject == null) {
        return;
      }
      ApplicationManager.getApplication().getService(ProjectDataManager.class).importData(externalProject, myProject, true);
    }

    @Override
    public void onFailure(@NotNull String errorMessage, @Nullable String errorDetails) {
      LOG.warn(errorMessage + "\n" + errorDetails);
    }
  }
}
