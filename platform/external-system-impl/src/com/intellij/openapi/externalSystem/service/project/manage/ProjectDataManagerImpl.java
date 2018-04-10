/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.*;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.impl.ProjectImpl;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static com.intellij.util.containers.ContainerUtil.map2Array;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services} and provides entry points for project data management.
 *
 * @author Denis Zhdanov
 * @since 4/16/13 11:38 AM
 */
public class ProjectDataManagerImpl implements ProjectDataManager {

  private static final Logger LOG = Logger.getInstance(ProjectDataManagerImpl.class);
  private static final com.intellij.openapi.util.Key<Boolean> DATA_READY =
    com.intellij.openapi.util.Key.create("externalSystem.data.ready");

  @NotNull private final NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?, ?>>>> myServices;

  public static ProjectDataManagerImpl getInstance() {
    ProjectDataManager service = ServiceManager.getService(ProjectDataManager.class);
    return (ProjectDataManagerImpl)service;
  }

  public ProjectDataManagerImpl() {
    myServices = new NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?, ?>>>>() {
      @NotNull
      @Override
      protected Map<Key<?>, List<ProjectDataService<?, ?>>> compute() {
        Map<Key<?>, List<ProjectDataService<?, ?>>> result = ContainerUtilRt.newHashMap();
        for (ProjectDataService<?, ?> service : ProjectDataService.EP_NAME.getExtensions()) {
          List<ProjectDataService<?, ?>> services = result.get(service.getTargetDataKey());
          if (services == null) {
            result.put(service.getTargetDataKey(), services = ContainerUtilRt.newArrayList());
          }
          services.add(service);
        }

        for (List<ProjectDataService<?, ?>> services : result.values()) {
          ExternalSystemApiUtil.orderAwareSort(services);
        }
        return result;
      }
    };
  }

  @SuppressWarnings("unchecked")
  @Override
  public void importData(@NotNull Collection<DataNode<?>> nodes,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider,
                         boolean synchronous) {
    if (project.isDisposed()) return;

    MultiMap<Key<?>, DataNode<?>> grouped = ExternalSystemApiUtil.recursiveGroup(nodes);
    for (Key<?> key : myServices.getValue().keySet()) {
      if (!grouped.containsKey(key)) {
        grouped.put(key, Collections.emptyList());
      }
    }

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

    List<Runnable> onSuccessImportTasks = ContainerUtil.newSmartList();
    List<Runnable> onFailureImportTasks = ContainerUtil.newSmartList();
    try {
      final Set<Map.Entry<Key<?>, Collection<DataNode<?>>>> entries = grouped.entrySet();
      final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
      if (indicator != null) {
        indicator.setIndeterminate(false);
      }
      final int size = entries.size();
      int count = 0;
      List<Runnable> postImportTasks = ContainerUtil.newSmartList();
      for (Map.Entry<Key<?>, Collection<DataNode<?>>> entry : entries) {
        if (indicator != null) {
          String message = ExternalSystemBundle.message(
            "progress.update.text", projectSystemId != null ? projectSystemId.getReadableName() : "",
            "Refresh " + getReadableText(entry.getKey()));
          indicator.setText(message);
          indicator.setFraction((double)count++ / size);
        }
        doImportData(entry.getKey(), entry.getValue(), projectData, project, modelsProvider,
                     postImportTasks, onSuccessImportTasks, onFailureImportTasks);
      }

      for (Runnable postImportTask : postImportTasks) {
        postImportTask.run();
      }

      commit(modelsProvider, project, synchronous, "Imported data");
      if (indicator != null) {
        indicator.setIndeterminate(true);
      }

      project.getMessageBus().syncPublisher(ProjectDataImportListener.TOPIC)
        .onImportFinished(projectData != null ? projectData.getLinkedExternalProjectPath() : null);
    }
    catch (Throwable t) {
      runFinalTasks(synchronous, onFailureImportTasks);
      dispose(modelsProvider, project, synchronous);
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
    runFinalTasks(synchronous, onSuccessImportTasks);
  }

  private static void runFinalTasks(boolean synchronous, List<Runnable> tasks) {
    Runnable runnable = () -> {
      for (Runnable task : ContainerUtil.reverse(tasks)) {
        task.run();
      }
    };
    if (synchronous) {
      try {
        runnable.run();
      }
      catch (Exception e) {
        LOG.warn(e);
      }
    }
    else {
      ApplicationManager.getApplication().invokeLater(runnable);
    }
  }

  @NotNull
  private static String getReadableText(@NotNull Key key) {
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

  @Override
  public <T> void importData(@NotNull Collection<DataNode<T>> nodes, @NotNull Project project, boolean synchronous) {
    Collection<DataNode<?>> dummy = ContainerUtil.newSmartList();
    dummy.addAll(nodes);
    importData(dummy, project, new IdeModifiableModelsProviderImpl(project), synchronous);
  }

  @Override
  public <T> void importData(@NotNull DataNode<T> node,
                             @NotNull Project project,
                             @NotNull IdeModifiableModelsProvider modelsProvider,
                             boolean synchronous) {
    Collection<DataNode<?>> dummy = ContainerUtil.newSmartList();
    dummy.add(node);
    importData(dummy, project, modelsProvider, synchronous);
  }

  @Override
  public <T> void importData(@NotNull DataNode<T> node,
                             @NotNull Project project,
                             boolean synchronous) {
    importData(node, project, new IdeModifiableModelsProviderImpl(project), synchronous);
  }

  @SuppressWarnings("unchecked")
  private <T> void doImportData(@NotNull Key<T> key,
                                @NotNull Collection<DataNode<?>> nodes,
                                @Nullable final ProjectData projectData,
                                @NotNull final Project project,
                                @NotNull final IdeModifiableModelsProvider modifiableModelsProvider,
                                @NotNull final List<Runnable> postImportTasks,
                                @NotNull final List<Runnable> onSuccessImportTasks,
                                @NotNull final List<Runnable> onFailureImportTasks) {
    if (project.isDisposed()) return;
    if (project instanceof ProjectImpl) {
      assert ((ProjectImpl)project).isComponentsCreated();
    }

    final List<DataNode<T>> toImport = ContainerUtil.newSmartList();
    final List<DataNode<T>> toIgnore = ContainerUtil.newSmartList();

    for (DataNode node : nodes) {
      if (!key.equals(node.getKey())) continue;

      if (node.isIgnored()) {
        toIgnore.add(node);
      }
      else {
        toImport.add(node);
      }
    }

    ensureTheDataIsReadyToUse((Collection)toImport);

    final List<ProjectDataService<?, ?>> services = myServices.getValue().get(key);
    if (services == null) {
      LOG.warn(String.format(
        "Can't import data nodes '%s'. Reason: no service is registered for key %s. Available services for %s",
        toImport, key, myServices.getValue().keySet()
      ));
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
          ensureTheDataIsReadyToUse((Collection)toIgnore);
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

    if (services != null && projectData != null) {
      postImportTasks.add(() -> {
        for (ProjectDataService<?, ?> service : services) {
          if (service instanceof AbstractProjectDataService) {
            final long taskStartTime = System.currentTimeMillis();
            ((AbstractProjectDataService)service).postProcess(toImport, projectData, project, modifiableModelsProvider);
            if (LOG.isDebugEnabled()) {
              final long taskTimeInMs = (System.currentTimeMillis() - taskStartTime);
              LOG.debug(String.format("Service %s run post import task in %d ms", service.getClass().getSimpleName(), taskTimeInMs));
            }
          }
        }
      });
      onFailureImportTasks.add(() -> {
        for (ProjectDataService<?, ?> service : services) {
          if (service instanceof AbstractProjectDataService) {
            final long taskStartTime = System.currentTimeMillis();
            ((AbstractProjectDataService)service).onFailureImport(project);
            if (LOG.isDebugEnabled()) {
              final long taskTimeInMs = (System.currentTimeMillis() - taskStartTime);
              LOG.debug(String.format("Service %s run failure import task in %d ms", service.getClass().getSimpleName(), taskTimeInMs));
            }
          }
        }
      });
      onSuccessImportTasks.add(() -> {
        IdeModelsProvider modelsProvider = new IdeModelsProviderImpl(project);
        for (ProjectDataService<?, ?> service : services) {
          if (service instanceof AbstractProjectDataService) {
            final long taskStartTime = System.currentTimeMillis();
            ((AbstractProjectDataService)service).onSuccessImport(toImport, projectData, project, modelsProvider);
            if (LOG.isDebugEnabled()) {
              final long taskTimeInMs = (System.currentTimeMillis() - taskStartTime);
              LOG.debug(String.format("Service %s run success import task in %d ms", service.getClass().getSimpleName(), taskTimeInMs));
            }
          }
        }
      });
    }
  }

  @Override
  public void ensureTheDataIsReadyToUse(@Nullable DataNode dataNode) {
    if (dataNode == null) return;
    if (Boolean.TRUE.equals(dataNode.getUserData(DATA_READY))) return;

    ExternalSystemApiUtil.visit(dataNode, dataNode1 -> {
      prepareDataToUse(dataNode1);
      dataNode1.putUserData(DATA_READY, Boolean.TRUE);
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
      List<ProjectDataService<?, ?>> services = myServices.getValue().get(key);
      for (ProjectDataService service : services) {
        final long removeStartTime = System.currentTimeMillis();
        service.removeData(new Computable.PredefinedValueComputable<Collection>(toRemove), toIgnore, projectData, project, modelsProvider);
        if (LOG.isDebugEnabled()) {
          final long removeTimeInMs = System.currentTimeMillis() - removeStartTime;
          LOG.debug(String.format("Service %s removed data in %d ms", service.getClass().getSimpleName(), removeTimeInMs));
        }
      }

      commit(modelsProvider, project, synchronous, "Removed data");
    }
    catch (Throwable t) {
      dispose(modelsProvider, project, synchronous);
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
  }

  public <E, I> void removeData(@NotNull Key<E> key,
                                @NotNull Collection<I> toRemove,
                                @NotNull final Collection<DataNode<E>> toIgnore,
                                @NotNull final ProjectData projectData,
                                @NotNull Project project,
                                boolean synchronous) {
    removeData(key, toRemove, toIgnore, projectData, project, new IdeModifiableModelsProviderImpl(project), synchronous);
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

  private void ensureTheDataIsReadyToUse(@NotNull Collection<DataNode<?>> nodes) {
    for (DataNode<?> node : nodes) {
      ensureTheDataIsReadyToUse(node);
    }
  }

  private void prepareDataToUse(@NotNull DataNode dataNode) {
    final Map<Key<?>, List<ProjectDataService<?, ?>>> servicesByKey = myServices.getValue();
    List<ProjectDataService<?, ?>> services = servicesByKey.get(dataNode.getKey());
    if (services != null) {
      try {
        dataNode.prepareData(map2Array(services, ClassLoader.class, service -> service.getClass().getClassLoader()));
      }
      catch (Exception e) {
        LOG.debug(e);
        dataNode.clear(true);
      }
    }
  }

  private static void commit(@NotNull final IdeModifiableModelsProvider modelsProvider,
                             @NotNull Project project,
                             boolean synchronous,
                             @NotNull final String commitDesc) {
    ExternalSystemApiUtil.executeProjectChangeAction(synchronous, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        final long startTime = System.currentTimeMillis();
        modelsProvider.commit();
        final long timeInMs = System.currentTimeMillis() - startTime;
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
