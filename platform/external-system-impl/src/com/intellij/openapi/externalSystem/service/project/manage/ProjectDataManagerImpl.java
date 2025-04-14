// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.diagnostic.StartUpPerformanceService;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.*;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemSyncActionsCollector;
import com.intellij.openapi.externalSystem.statistics.Phase;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.workspace.storage.MutableEntityStorage;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.ui.EDT;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services} and provides entry points for project data management.
 */
@ApiStatus.Internal
public final class ProjectDataManagerImpl implements ProjectDataManager {
  private static final Logger LOG = Logger.getInstance(ProjectDataManagerImpl.class);
  private static final Function<ProjectDataService<?, ?>, Key<?>> KEY_MAPPER = ProjectDataService::getTargetDataKey;

  private final Lock myLock = new ReentrantLock();

  public static ProjectDataManagerImpl getInstance() {
    return (ProjectDataManagerImpl)ProjectDataManager.getInstance();
  }

  @Override
  public @NotNull List<WorkspaceDataService<?>> findWorkspaceService(@NotNull Key<?> key) {
    List<WorkspaceDataService<?>> result = new ArrayList<>(
      WorkspaceDataService.EP_NAME.getByGroupingKey(key, ProjectDataManagerImpl.class, WorkspaceDataService::getTargetDataKey));
    ExternalSystemApiUtil.orderAwareSort(result);
    return result;
  }

  @Override
  public @NotNull List<ProjectDataService<?, ?>> findService(@NotNull Key<?> key) {
    List<ProjectDataService<?, ?>> result = new ArrayList<>(ProjectDataService.EP_NAME
                                                              .getByGroupingKey(key, ProjectDataManagerImpl.class, KEY_MAPPER));
    ExternalSystemApiUtil.orderAwareSort(result);
    return result;
  }

  @Override
  public <T> void importData(@NotNull DataNode<T> node, @NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (!EDT.isCurrentThreadEdt() && app.holdsReadLock()) {
      throw new IllegalStateException("importData() must not be called with a global read lock on a background thread. " +
                                      "It will deadlock committing project model changes in write action");
    }

    if (EDT.isCurrentThreadEdt()) {
      if (!myLock.tryLock()) {
        throw new IllegalStateException("importData() can not wait on write thread for imports on background threads." +
                                        " Consider running importData() on background thread.");
      }
    }
    else {
      myLock.lock();
    }
    try {
      importData(node, project, createModifiableModelsProvider(project));
    }
    finally {
      myLock.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> void importData(@NotNull DataNode<T> node,
                             @NotNull Project project,
                             @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (project.isDisposed()) return;

    MultiMap<Key<?>, DataNode<?>> grouped = ExternalSystemApiUtil.recursiveGroup(Collections.singletonList(node));

    final Collection<DataNode<?>> projects = grouped.get(ProjectKeys.PROJECT);
    // only one project(can be multi-module project) expected for per single import
    assert projects.size() == 1 || projects.isEmpty();

    final DataNode<ProjectData> projectNode = (DataNode<ProjectData>)ContainerUtil.getFirstItem(projects);
    final ProjectData projectData;
    ProjectSystemId projectSystemId;
    if (projectNode != null) {
      projectData = projectNode.getData();
      projectSystemId = projectNode.getData().getOwner();
      ExternalProjectsDataStorage.getInstance(project).saveInclusionSettings(projectNode);
    }
    else {
      projectData = null;
      DataNode<ModuleData> aModuleNode = (DataNode<ModuleData>)ContainerUtil.getFirstItem(grouped.get(ProjectKeys.MODULE));
      projectSystemId = aModuleNode != null ? aModuleNode.getData().getOwner() : null;
    }

    if (projectSystemId != null) {
      ExternalSystemUtil.scheduleExternalViewStructureUpdate(project, projectSystemId);
    }

    List<Runnable> onSuccessImportTasks = new SmartList<>();
    List<Runnable> onFailureImportTasks = new SmartList<>();

    final Collection<DataNode<?>> operationDescriptorNodes =
      grouped.getOrPut(ExternalSystemOperationDescriptor.OPERATION_DESCRIPTOR_KEY, () ->
        new DataNode<>(ExternalSystemOperationDescriptor.OPERATION_DESCRIPTOR_KEY, new ExternalSystemOperationDescriptor(), null)
    );
    final ExternalSystemOperationDescriptor operationDescriptor =
      (ExternalSystemOperationDescriptor)ContainerUtil.getFirstItem(operationDescriptorNodes)
        .getData();

    long allStartTime = System.currentTimeMillis();
    long activityId = operationDescriptor.getActivityId();

    ExternalSystemSyncActionsCollector.logPhaseStarted(project, activityId, Phase.DATA_SERVICES);

    boolean importSucceeded = false;
    int errorsCount = 0;

    String projectPath = ObjectUtils.doIfNotNull(projectData, ProjectData::getLinkedExternalProjectPath);
    var topic = project.getMessageBus()
      .syncPublisher(ProjectDataImportListener.TOPIC);

    topic.onImportStarted(projectPath);
    Span dataServicesSpan = ExternalSystemTelemetryUtil.getTracer(projectSystemId)
      .spanBuilder("ProjectDataServices")
      .startSpan();
    try (Scope ignore = dataServicesSpan.makeCurrent()) {
      // keep order of services execution
      final Set<Key<?>> allKeys = new TreeSet<>(grouped.keySet());
      ProjectDataService.EP_NAME.forEachExtensionSafe(dataService -> allKeys.add(dataService.getTargetDataKey()));
      WorkspaceDataService.EP_NAME.forEachExtensionSafe(dataService -> allKeys.add(dataService.getTargetDataKey()));

      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setIndeterminate(false);
      }
      final int size = allKeys.size();
      int count = 0;
      List<Runnable> postImportTasks = new SmartList<>();
      for (Key<?> key : allKeys) {
        if (indicator != null) {
          String message = ExternalSystemBundle.message(
            "progress.update.text", projectSystemId != null ? projectSystemId.getReadableName() : "",
            ExternalSystemBundle.message("progress.update.refresh", getReadableText(key)));
          indicator.setText(message);
          indicator.setFraction((double)count++ / size);
        }
        doImportData(key, grouped.get(key), projectSystemId, projectData, project, modelsProvider,
                     postImportTasks, onSuccessImportTasks, onFailureImportTasks);
      }

      ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "postImportTasks", span -> {
        for (Runnable postImportTask : postImportTasks) {
          postImportTask.run();
        }
      });

      commit(modelsProvider, project, true, "Imported data", activityId, projectSystemId);
      if (indicator != null) {
        indicator.setIndeterminate(true);
      }

      topic.onImportFinished(projectPath);
      importSucceeded = true;
    }
    catch (Throwable t) {
      dataServicesSpan.recordException(t);
      dataServicesSpan.setStatus(StatusCode.ERROR);
      errorsCount += 1;
      topic.onImportFailed(projectPath, t);
      ExternalSystemSyncActionsCollector.logError(null, activityId, t);
      LOG.error(t);
      //noinspection ConstantConditions
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    finally {
      if (importSucceeded) {
        ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "runFinalTasks",
                                                __ -> runFinalTasks(project, projectPath, onSuccessImportTasks));
      }
      else {
        ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "runFinalTasks",
                                                __ -> runFinalTasks(project, projectPath, onFailureImportTasks));
      }
      if (!importSucceeded) {
        dispose(modelsProvider, project, true);
      }

      long timeMs = System.currentTimeMillis() - allStartTime;
      dataServicesSpan.end();
      ExternalSystemSyncActionsCollector.logPhaseFinished(project, activityId, Phase.DATA_SERVICES, timeMs, errorsCount);
      ExternalSystemSyncActionsCollector.logSyncFinished(project, activityId, importSucceeded);

      Application app = ApplicationManager.getApplication();
      if (!app.isUnitTestMode() && !app.isHeadlessEnvironment()) {
        StartUpPerformanceService.Companion.getInstance().reportStatistics(project);
      }
    }
  }

  private static void runFinalTasks(
    @NotNull Project project,
    @Nullable String projectPath,
    @NotNull List<? extends Runnable> tasks
  ) {
    var topic = project.getMessageBus()
      .syncPublisher(ProjectDataImportListener.TOPIC);

    topic.onFinalTasksStarted(projectPath);
    try {
      ContainerUtil.reverse(tasks).forEach(Runnable::run);
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    finally {
      topic.onFinalTasksFinished(projectPath);
    }
  }

  private static @NotNull String getReadableText(@NotNull Key<?> key) {
    StringBuilder buffer = new StringBuilder();
    String s = key.toString();
    for (int i = 0; i < s.length(); i++) {
      char currChar = s.charAt(i);
      if (Character.isUpperCase(currChar)) {
        if (i != 0) {
          buffer.append(' ');
        }
        buffer.append(StringUtil.toLowerCase(currChar));
      }
      else {
        buffer.append(currChar);
      }
    }
    return buffer.toString();
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private <T> void doImportData(@NotNull Key<T> key,
                                @NotNull Collection<? extends DataNode<?>> nodes,
                                @Nullable ProjectSystemId projectSystemId,
                                final @Nullable ProjectData projectData,
                                final @NotNull Project project,
                                final @NotNull IdeModifiableModelsProvider modifiableModelsProvider,
                                final @NotNull List<Runnable> postImportTasks,
                                final @NotNull List<Runnable> onSuccessImportTasks,
                                final @NotNull List<Runnable> onFailureImportTasks) {
    if (project.isDisposed()) {
      return;
    }

    final Collection<DataNode<?>> toImport = new SmartList<>();
    final Collection<DataNode<?>> toIgnore = new SmartList<>();

    for (DataNode<?> node : nodes) {
      if (!key.equals(node.getKey())) continue;

      if (node.isIgnored()) {
        toIgnore.add(node);
      }
      else {
        toImport.add(node);
      }
    }

    ensureTheDataIsReadyToUse(toImport);

    @NotNull List<ProjectDataService<?, ?>> services = findService(key);
    @NotNull List<WorkspaceDataService<?>> workspaceServices = findWorkspaceService(key);
    if (services.isEmpty() && workspaceServices.isEmpty()) {
      LOG.debug(String.format("No data service is registered for %s", key));
    }
    else {
      for (ProjectDataService<?, ?> service : services) {
        final long importStartTime = System.currentTimeMillis();
        String dataServiceName = service.getClass().getSimpleName();
        ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, dataServiceName,
                                                (span) -> ((ProjectDataService)service).importData(toImport, projectData, project,
                                                                                                   modifiableModelsProvider));
        if (LOG.isDebugEnabled()) {
          final long importTimeInMs = (System.currentTimeMillis() - importStartTime);
          LOG.debug(String.format("Service %s imported data in %d ms", dataServiceName, importTimeInMs));
        }

        if (projectData != null) {
          ensureTheDataIsReadyToUse(toIgnore);
          final long removeStartTime = System.currentTimeMillis();
          final Computable<Collection<?>> orphanIdeDataComputable =
            ((ProjectDataService)service).computeOrphanData(toImport, projectData, project, modifiableModelsProvider);
          ((ProjectDataService)service).removeData(orphanIdeDataComputable, toIgnore, projectData, project, modifiableModelsProvider);
          if (LOG.isDebugEnabled()) {
            final long removeTimeInMs = (System.currentTimeMillis() - removeStartTime);
            LOG.debug(String.format("Service %s computed and removed data in %d ms", dataServiceName, removeTimeInMs));
          }
        }
      }

      for (WorkspaceDataService<?> service : workspaceServices) {
        final long importStartTime = System.currentTimeMillis();
        if (modifiableModelsProvider instanceof IdeModifiableModelsProviderImpl) {
          MutableEntityStorage mutableStorage = ((IdeModifiableModelsProviderImpl)modifiableModelsProvider).getActualStorageBuilder();
          ((WorkspaceDataService)service).importData(toImport, projectData, project, mutableStorage);
        }
        else {
          LOG.warn(String.format("MutableEntityStorage missing, models provider is %s", modifiableModelsProvider.getClass().getName()));
        }
        if (LOG.isDebugEnabled()) {
          final long importTimeInMs = (System.currentTimeMillis() - importStartTime);
          LOG.debug(String.format("Workspace service %s imported data in %d ms", service.getClass().getSimpleName(), importTimeInMs));
        }
      }
    }

    if (!services.isEmpty() && projectData != null) {
      postImportTasks.add(() -> {
        for (ProjectDataService<?, ?> service : services) {
          final long taskStartTime = System.currentTimeMillis();
          ((ProjectDataService)service).postProcess(toImport, projectData, project, modifiableModelsProvider);
          if (LOG.isDebugEnabled()) {
            final long taskTimeInMs = (System.currentTimeMillis() - taskStartTime);
            LOG.debug(String.format("Service %s run post import task in %d ms", service.getClass().getSimpleName(), taskTimeInMs));
          }
        }
      });
      onFailureImportTasks.add(() -> {
        for (ProjectDataService<?, ?> service : services) {
          final long taskStartTime = System.currentTimeMillis();
          service.onFailureImport(project);
          if (LOG.isDebugEnabled()) {
            final long taskTimeInMs = (System.currentTimeMillis() - taskStartTime);
            LOG.debug(String.format("Service %s run failure import task in %d ms", service.getClass().getSimpleName(), taskTimeInMs));
          }
        }
      });
      onSuccessImportTasks.add(() -> {
        IdeModelsProvider modelsProvider = new IdeModelsProviderImpl(project);
        for (ProjectDataService<?, ?> service : services) {
          final long taskStartTime = System.currentTimeMillis();
          ((ProjectDataService)service).onSuccessImport(toImport, projectData, project, modelsProvider);
          if (LOG.isDebugEnabled()) {
            final long taskTimeInMs = (System.currentTimeMillis() - taskStartTime);
            LOG.debug(String.format("Service %s run success import task in %d ms", service.getClass().getSimpleName(), taskTimeInMs));
          }
        }
      });
    }
  }

  @Override
  public void ensureTheDataIsReadyToUse(@Nullable DataNode startNode) {
    if (startNode == null || startNode.isReady()) {
      return;
    }

    DeduplicateVisitorsSupplier supplier = new DeduplicateVisitorsSupplier();
    ((DataNode<?>)startNode).visit(dataNode -> {
      if (dataNode.validateData()) {
        dataNode.visitData(supplier.getVisitor(dataNode.getKey()));
      }
    });
  }

  @SuppressWarnings("unchecked")
  public <E, I> void removeData(@NotNull Key<E> key,
                                @NotNull Collection<I> toRemove,
                                final @NotNull Collection<DataNode<E>> toIgnore,
                                final @NotNull ProjectData projectData,
                                @NotNull Project project,
                                final @NotNull IdeModifiableModelsProvider modelsProvider,
                                boolean synchronous) {
    try {
      List<ProjectDataService<?, ?>> services = findService(key);
      for (ProjectDataService service : services) {
        final long removeStartTime = System.currentTimeMillis();
        service.removeData(new Computable.PredefinedValueComputable<Collection>(toRemove), toIgnore, projectData, project, modelsProvider);
        if (LOG.isDebugEnabled()) {
          final long removeTimeInMs = System.currentTimeMillis() - removeStartTime;
          LOG.debug(String.format("Service %s removed data in %d ms", service.getClass().getSimpleName(), removeTimeInMs));
        }
      }

      commit(modelsProvider, project, synchronous, "Removed data", null, projectData.getOwner());
    }
    catch (Throwable t) {
      dispose(modelsProvider, project, synchronous);
      ExceptionUtil.rethrow(t);
    }
  }

  public <E, I> void removeData(@NotNull Key<E> key,
                                @NotNull Collection<I> toRemove,
                                final @NotNull Collection<DataNode<E>> toIgnore,
                                final @NotNull ProjectData projectData,
                                @NotNull Project project,
                                boolean synchronous) {
    removeData(key, toRemove, toIgnore, projectData, project, createModifiableModelsProvider(project), synchronous);
  }

  public void updateExternalProjectData(@NotNull Project project, @NotNull ExternalProjectInfo externalProjectInfo) {
    if (!project.isDisposed()) {
      ExternalProjectsManagerImpl.getInstance(project).updateExternalProjectData(externalProjectInfo);
    }
  }

  @Override
  public @Nullable ExternalProjectInfo getExternalProjectData(@NotNull Project project,
                                                              @NotNull ProjectSystemId projectSystemId,
                                                              @NotNull String externalProjectPath) {
    return !project.isDisposed() ? ExternalProjectsDataStorage.getInstance(project).get(projectSystemId, externalProjectPath) : null;
  }

  @Override
  public @NotNull @Unmodifiable Collection<ExternalProjectInfo> getExternalProjectsData(@NotNull Project project, @NotNull ProjectSystemId projectSystemId) {
    if (!project.isDisposed()) {
      return ExternalProjectsDataStorage.getInstance(project).list(projectSystemId);
    }
    else {
      return ContainerUtil.emptyList();
    }
  }

  @Override
  public @NotNull IdeModifiableModelsProvider createModifiableModelsProvider(@NotNull Project project) {
    return new IdeModifiableModelsProviderImpl(project);
  }

  private void ensureTheDataIsReadyToUse(@NotNull Collection<? extends DataNode<?>> nodes) {
    for (DataNode<?> node : nodes) {
      ensureTheDataIsReadyToUse(node);
    }
  }

  private static void commit(final @NotNull IdeModifiableModelsProvider modelsProvider,
                             @NotNull Project project,
                             boolean synchronous,
                             final @NotNull String commitDesc,
                             @Nullable Long activityId,
                             @Nullable ProjectSystemId projectSystemId) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, project, Context.current().wrap(() -> {
      ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "WorkspaceModelApply", (ignore) -> {
        if (activityId != null) {
          ExternalSystemSyncActionsCollector.logPhaseStarted(project, activityId, Phase.WORKSPACE_MODEL_APPLY);
        }
        final long startTime = System.currentTimeMillis();
        modelsProvider.commit();
        final long timeInMs = System.currentTimeMillis() - startTime;
        if (activityId != null) {
          ExternalSystemSyncActionsCollector.logPhaseFinished(project, activityId, Phase.WORKSPACE_MODEL_APPLY, timeInMs);
        }
        LOG.debug(String.format("%s committed in %d ms", commitDesc, timeInMs));
      });
    }));
  }

  private static void dispose(final @NotNull IdeModifiableModelsProvider modelsProvider,
                              @NotNull Project project,
                              boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, project, () -> modelsProvider.dispose());
  }
}
