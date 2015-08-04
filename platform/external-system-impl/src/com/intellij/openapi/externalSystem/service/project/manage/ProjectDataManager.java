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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.intellij.util.containers.ContainerUtil.map2Array;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services} and provides entry points for project data management.
 *
 * @author Denis Zhdanov
 * @since 4/16/13 11:38 AM
 */
public class ProjectDataManager {

  private static final Logger LOG = Logger.getInstance("#" + ProjectDataManager.class.getName());
  private static final com.intellij.openapi.util.Key<Boolean> DATA_READY =
    com.intellij.openapi.util.Key.create("externalSystem.data.ready");

  @NotNull private final NotNullLazyValue<Map<Key<?>, List<ProjectDataService<?, ?>>>> myServices;
  private final PlatformFacade myPlatformFacade;

  public static ProjectDataManager getInstance() {
    return ServiceManager.getService(ProjectDataManager.class);
  }

  public ProjectDataManager(@NotNull PlatformFacade platformFacade) {
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
    myPlatformFacade = platformFacade;
  }

  @Nullable
  public ProjectDataService<?, ?> getDataService(Key<?> key) {
    final List<ProjectDataService<?, ?>> dataServices = myServices.getValue().get(key);
    assert dataServices == null || dataServices.isEmpty() || dataServices.size() == 1;
    return ContainerUtil.getFirstItem(dataServices);
  }

  @SuppressWarnings("unchecked")
  public void importData(@NotNull Collection<DataNode<?>> nodes,
                         @NotNull final Project project,
                         @NotNull PlatformFacade platformFacade,
                         boolean synchronous) {
    if (project.isDisposed()) return;

    MultiMap<Key<?>, DataNode<?>> grouped = ExternalSystemApiUtil.recursiveGroup(nodes);
    for (Key<?> key : myServices.getValue().keySet()) {
      if (!grouped.containsKey(key)) {
        grouped.put(key, Collections.<DataNode<?>>emptyList());
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

    for (Map.Entry<Key<?>, Collection<DataNode<?>>> entry : grouped.entrySet()) {
      doImportData(entry.getKey(), entry.getValue(), projectData, project, platformFacade, synchronous);
    }
  }

  /**
   * @deprecated to be removed in v15, use {@link #importData(Collection, Project, boolean)}
   */
  @Deprecated
  public <T> void importData(@NotNull Key<T> key, @NotNull Collection<DataNode<T>> nodes, @NotNull Project project, boolean synchronous) {
    importData(nodes, project, synchronous);
  }

  public <T> void importData(@NotNull Collection<DataNode<T>> nodes, @NotNull Project project, boolean synchronous) {
    Collection<DataNode<?>> dummy = ContainerUtil.newSmartList();
    for (DataNode<T> node : nodes) {
      dummy.add(node);
    }
    importData(dummy, project, myPlatformFacade, synchronous);
  }

  public <T> void importData(@NotNull DataNode<T> node,
                             @NotNull Project project,
                             @NotNull PlatformFacade platformFacade,
                             boolean synchronous) {
    Collection<DataNode<?>> dummy = ContainerUtil.newSmartList();
    dummy.add(node);
    importData(dummy, project, platformFacade, synchronous);
  }

  public <T> void importData(@NotNull DataNode<T> node,
                             @NotNull Project project,
                             boolean synchronous) {
    importData(node, project, myPlatformFacade, synchronous);
  }

  @SuppressWarnings("unchecked")
  private <T> void doImportData(@NotNull Key<T> key,
                                @NotNull Collection<DataNode<?>> nodes,
                                @Nullable ProjectData projectData,
                                @NotNull Project project,
                                @NotNull PlatformFacade platformFacade,
                                boolean synchronous) {
    if (project.isDisposed()) return;

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

    List<ProjectDataService<?, ?>> services = myServices.getValue().get(key);
    if (services == null) {
      LOG.warn(String.format(
        "Can't import data nodes '%s'. Reason: no service is registered for key %s. Available services for %s",
        toImport, key, myServices.getValue().keySet()
      ));
    }
    else {
      for (ProjectDataService<?, ?> service : services) {
        if (service instanceof ProjectDataServiceEx) {
          ((ProjectDataServiceEx<T, ?>)service).importData(toImport, projectData, project, platformFacade, synchronous);
        }
        else {
          ((ProjectDataService<T, ?>)service).importData(toImport, project, synchronous);
        }
      }
    }

    ensureTheDataIsReadyToUse((Collection)toIgnore);

    if (services != null && projectData != null) {
      for (ProjectDataService<?, ?> service : services) {
        if (service instanceof ProjectDataServiceEx) {
          final ProjectDataServiceEx dataServiceEx = (ProjectDataServiceEx)service;
          final Computable<Collection<?>> orphanIdeDataComputable =
            dataServiceEx.computeOrphanData(toImport, projectData, project, platformFacade);
          dataServiceEx.removeData(orphanIdeDataComputable, toIgnore, projectData, project, platformFacade, synchronous);
        }
      }
    }
  }

  public void ensureTheDataIsReadyToUse(@Nullable DataNode dataNode) {
    if (dataNode == null) return;
    if (Boolean.TRUE.equals(dataNode.getUserData(DATA_READY))) return;

    ExternalSystemApiUtil.visit(dataNode, new Consumer<DataNode<?>>() {
      @Override
      public void consume(DataNode dataNode) {
        prepareDataToUse(dataNode);
        dataNode.putUserData(DATA_READY, Boolean.TRUE);
      }
    });
  }

  @SuppressWarnings("unchecked")
  @Deprecated
  public <E, I> void removeData(@NotNull Key<E> key, @NotNull Collection<I> toRemove, @NotNull Project project, boolean synchronous) {
    List<ProjectDataService<?, ?>> services = myServices.getValue().get(key);
    for (ProjectDataService service : services) {
      service.removeData(toRemove, project, synchronous);
    }
  }

  @SuppressWarnings("unchecked")
  public <E, I> void removeData(@NotNull Key<E> key,
                                @NotNull Collection<I> toRemove,
                                @NotNull final Collection<DataNode<E>> toIgnore,
                                @NotNull final ProjectData projectData,
                                @NotNull Project project,
                                @NotNull PlatformFacade platformFacade,
                                boolean synchronous) {
    List<ProjectDataService<?, ?>> services = myServices.getValue().get(key);
    for (ProjectDataService service : services) {
      if (service instanceof ProjectDataServiceEx) {
        ((ProjectDataServiceEx)service).removeData(new Computable.PredefinedValueComputable<Collection>(toRemove),
                                                   toIgnore, projectData, project, platformFacade, synchronous);
      }
      else {
        service.removeData(toRemove, project, synchronous);
      }
    }
  }

  public <E, I> void removeData(@NotNull Key<E> key,
                                @NotNull Collection<I> toRemove,
                                @NotNull final Collection<DataNode<E>> toIgnore,
                                @NotNull final ProjectData projectData,
                                @NotNull Project project,
                                boolean synchronous) {
    removeData(key, toRemove, toIgnore, projectData, project, myPlatformFacade, synchronous);
  }

  public void updateExternalProjectData(@NotNull Project project, @NotNull ExternalProjectInfo externalProjectInfo) {
    if (!project.isDisposed()) {
      ExternalProjectsManager.getInstance(project).updateExternalProjectData(externalProjectInfo);
    }
  }

  @Nullable
  public ExternalProjectInfo getExternalProjectData(@NotNull Project project,
                                                    @NotNull ProjectSystemId projectSystemId,
                                                    @NotNull String externalProjectPath) {
    return !project.isDisposed() ? ExternalProjectsDataStorage.getInstance(project).get(projectSystemId, externalProjectPath) : null;
  }

  @NotNull
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
        dataNode.prepareData(map2Array(services, ClassLoader.class, new Function<ProjectDataService<?, ?>, ClassLoader>() {
          @Override
          public ClassLoader fun(ProjectDataService<?, ?> service) {
            return service.getClass().getClassLoader();
          }
        }));
      }
      catch (Exception e) {
        LOG.debug(e);
        dataNode.clear(true);
      }
    }
  }
}
