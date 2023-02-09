// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.diagnostic.StartUpPerformanceService;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.diagnostic.ExternalSystemSyncDiagnostic;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.*;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemSyncActionsCollector;
import com.intellij.openapi.externalSystem.statistics.Phase;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services} and provides entry points for project data management.
 */
public final class ProjectDataManagerImpl implements ProjectDataManager {
  private static final Logger LOG = Logger.getInstance(ProjectDataManagerImpl.class);
  private static final Function<ProjectDataService<?, ?>, Key<?>> KEY_MAPPER = ProjectDataService::getTargetDataKey;

  private final Lock myLock = new ReentrantLock();

  public static ProjectDataManagerImpl getInstance() {
    return (ProjectDataManagerImpl)ProjectDataManager.getInstance();
  }

  @Override
  @NotNull
  public List<ProjectDataService<?, ?>> findService(@NotNull Key<?> key) {
    List<ProjectDataService<?, ?>> result = new ArrayList<>(ProjectDataService.EP_NAME
                                                              .getByGroupingKey(key, ProjectDataManagerImpl.class, KEY_MAPPER));
    ExternalSystemApiUtil.orderAwareSort(result);
    return result;
  }

  @Override
  public <T> void importData(@NotNull DataNode<T> node, @NotNull Project project) {
    Application app = ApplicationManager.getApplication();
    if (!app.isWriteIntentLockAcquired() && app.isReadAccessAllowed()) {
      throw new IllegalStateException("importData() must not be called with a global read lock on a background thread. " +
                                      "It will deadlock committing project model changes in write action");
    }

    if (app.isWriteIntentLockAcquired()) {
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
    final Collection<DataNode<?>> traceNodes = grouped.get(PerformanceTrace.TRACE_NODE_KEY);

    final PerformanceTrace trace;
    if (traceNodes.size() > 0) {
      trace = (PerformanceTrace)traceNodes.iterator().next().getData();
    }
    else {
      trace = new PerformanceTrace();
      grouped.putValue(PerformanceTrace.TRACE_NODE_KEY, new DataNode<>(PerformanceTrace.TRACE_NODE_KEY, trace, null));
    }

    long allStartTime = System.currentTimeMillis();
    long activityId = trace.getId();

    ExternalSystemSyncDiagnostic.getOrStartSpan(Phase.DATA_SERVICES.name(), (builder) ->
      builder.setParent(ExternalSystemSyncDiagnostic.getSpanContext(ExternalSystemSyncDiagnostic.gradleSyncSpanName)));
    ExternalSystemSyncActionsCollector.logPhaseStarted(project, activityId, Phase.DATA_SERVICES);

    boolean importSucceeded = false;
    int errorsCount = 0;

    String projectPath = ObjectUtils.doIfNotNull(projectData, ProjectData::getLinkedExternalProjectPath);
    var topic = project.getMessageBus()
      .syncPublisher(ProjectDataImportListener.TOPIC);

    topic.onImportStarted(projectPath);
    try {
      // keep order of services execution
      final Set<Key<?>> allKeys = new TreeSet<>(grouped.keySet());
      ProjectDataService.EP_NAME.forEachExtensionSafe(dataService -> allKeys.add(dataService.getTargetDataKey()));

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
        long startTime = System.currentTimeMillis();
        doImportData(key, grouped.get(key), projectData, project, modelsProvider,
                     postImportTasks, onSuccessImportTasks, onFailureImportTasks);
        trace.logPerformance("Data import by " + key, System.currentTimeMillis() - startTime);
      }

      for (Runnable postImportTask : postImportTasks) {
        postImportTask.run();
      }

      commit(modelsProvider, project, true, "Imported data", activityId);
      if (indicator != null) {
        indicator.setIndeterminate(true);
      }

      topic.onImportFinished(projectPath);
      importSucceeded = true;
    }
    catch (Throwable t) {
      errorsCount += 1;
      topic.onImportFailed(projectPath, t);
      ExternalSystemSyncActionsCollector.logError(null, activityId, t);
      //noinspection ConstantConditions
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    finally {
      if (importSucceeded) {
        runFinalTasks(project, projectPath, onSuccessImportTasks);
      }
      else {
        runFinalTasks(project, projectPath, onFailureImportTasks);
      }
      if (!importSucceeded) {
        dispose(modelsProvider, project, true);
      }

      long timeMs = System.currentTimeMillis() - allStartTime;
      trace.logPerformance("Data import total", timeMs);
      ExternalSystemSyncDiagnostic.endSpan(Phase.DATA_SERVICES.name());
      ExternalSystemSyncActionsCollector.logPhaseFinished(project, activityId, Phase.DATA_SERVICES, timeMs, errorsCount);
      ExternalSystemSyncActionsCollector.logSyncFinished(project, activityId, importSucceeded);
      ExternalSystemSyncDiagnostic.endSpan(ExternalSystemSyncDiagnostic.gradleSyncSpanName,
                                           (span) -> span.setAttribute("project", project.getName()));

      Application app = ApplicationManager.getApplication();
      if (!app.isUnitTestMode() && !app.isHeadlessEnvironment()) {
        StartUpPerformanceService.getInstance().reportStatistics(project);
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

  @NotNull
  private static String getReadableText(@NotNull Key<?> key) {
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
                                @Nullable final ProjectData projectData,
                                @NotNull final Project project,
                                @NotNull final IdeModifiableModelsProvider modifiableModelsProvider,
                                @NotNull final List<Runnable> postImportTasks,
                                @NotNull final List<Runnable> onSuccessImportTasks,
                                @NotNull final List<Runnable> onFailureImportTasks) {
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
    if (services.isEmpty()) {
      LOG.debug(String.format("No data service is registered for %s", key));
    }
    else {
      for (ProjectDataService<?, ?> service : services) {
        final long importStartTime = System.currentTimeMillis();
        ((ProjectDataService)service).importData(toImport, projectData, project, modifiableModelsProvider);
        if (LOG.isDebugEnabled()) {
          final long importTimeInMs = (System.currentTimeMillis() - importStartTime);
          LOG.debug(String.format("Service %s imported data in %d ms", service.getClass().getSimpleName(), importTimeInMs));
        }

        if (projectData != null) {
          ensureTheDataIsReadyToUse(toIgnore);
          final long removeStartTime = System.currentTimeMillis();
          final Computable<Collection<?>> orphanIdeDataComputable =
            ((ProjectDataService)service).computeOrphanData(toImport, projectData, project, modifiableModelsProvider);
          ((ProjectDataService)service).removeData(orphanIdeDataComputable, toIgnore, projectData, project, modifiableModelsProvider);
          if (LOG.isDebugEnabled()) {
            final long removeTimeInMs = (System.currentTimeMillis() - removeStartTime);
            LOG.debug(String.format("Service %s computed and removed data in %d ms", service.getClass().getSimpleName(), removeTimeInMs));
          }
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
                                @NotNull final Collection<DataNode<E>> toIgnore,
                                @NotNull final ProjectData projectData,
                                @NotNull Project project,
                                @NotNull final IdeModifiableModelsProvider modelsProvider,
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

      commit(modelsProvider, project, synchronous, "Removed data", null);
    }
    catch (Throwable t) {
      dispose(modelsProvider, project, synchronous);
      ExceptionUtil.rethrow(t);
    }
  }

  public <E, I> void removeData(@NotNull Key<E> key,
                                @NotNull Collection<I> toRemove,
                                @NotNull final Collection<DataNode<E>> toIgnore,
                                @NotNull final ProjectData projectData,
                                @NotNull Project project,
                                boolean synchronous) {
    removeData(key, toRemove, toIgnore, projectData, project, createModifiableModelsProvider(project), synchronous);
  }

  public void updateExternalProjectData(@NotNull Project project, @NotNull ExternalProjectInfo externalProjectInfo) {
    if (!project.isDisposed()) {
      ExternalProjectsManagerImpl.getInstance(project).updateExternalProjectData(externalProjectInfo);
    }
  }

  @Nullable
  @Override
  public ExternalProjectInfo getExternalProjectData(@NotNull Project project,
                                                    @NotNull ProjectSystemId projectSystemId,
                                                    @NotNull String externalProjectPath) {
    return !project.isDisposed() ? ExternalProjectsDataStorage.getInstance(project).get(projectSystemId, externalProjectPath) : null;
  }

  @NotNull
  @Override
  public Collection<ExternalProjectInfo> getExternalProjectsData(@NotNull Project project, @NotNull ProjectSystemId projectSystemId) {
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

  private static void commit(@NotNull final IdeModifiableModelsProvider modelsProvider,
                             @NotNull Project project,
                             boolean synchronous,
                             @NotNull final String commitDesc,
                             @Nullable Long activityId) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        ExternalSystemSyncDiagnostic.getOrStartSpan(Phase.WORKSPACE_MODEL_APPLY.name(), (builder) ->
          builder.setParent(ExternalSystemSyncDiagnostic.getSpanContext(ExternalSystemSyncDiagnostic.gradleSyncSpanName)));

        if (activityId != null) {
          ExternalSystemSyncActionsCollector.logPhaseStarted(project, activityId, Phase.WORKSPACE_MODEL_APPLY);
        }
        final long startTime = System.currentTimeMillis();
        modelsProvider.commit();
        final long timeInMs = System.currentTimeMillis() - startTime;
        if (activityId != null) {
          ExternalSystemSyncDiagnostic.endSpan(Phase.WORKSPACE_MODEL_APPLY.name());
          ExternalSystemSyncActionsCollector.logPhaseFinished(project, activityId, Phase.WORKSPACE_MODEL_APPLY, timeInMs);
        }
        LOG.debug(String.format("%s committed in %d ms", commitDesc, timeInMs));
      }
    });
  }

  private static void dispose(@NotNull final IdeModifiableModelsProvider modelsProvider,
                              @NotNull Project project,
                              boolean synchronous) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        modelsProvider.dispose();
      }
    });
  }
}
