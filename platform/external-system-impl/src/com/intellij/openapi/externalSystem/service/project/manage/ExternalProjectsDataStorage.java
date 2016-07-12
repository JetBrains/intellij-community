/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.*;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Property;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.*;

/**
 * @author Vladislav.Soroka
 * @since 9/18/2014
 */
@State(name = "ExternalProjectsData", storages = {@Storage(StoragePathMacros.WORKSPACE_FILE)})
public class ExternalProjectsDataStorage implements SettingsSavingComponent, PersistentStateComponent<ExternalProjectsDataStorage.State> {
  private static final Logger LOG = Logger.getInstance(ExternalProjectsDataStorage.class);

  private static final String STORAGE_VERSION = ExternalProjectsDataStorage.class.getSimpleName() + ".1";

  @NotNull
  private final Project myProject;
  private final Alarm myAlarm;
  @NotNull
  private final Map<Pair<ProjectSystemId, File>, InternalExternalProjectInfo> myExternalRootProjects =
    ContainerUtil.newConcurrentMap(ExternalSystemUtil.HASHING_STRATEGY);

  private final AtomicBoolean changed = new AtomicBoolean();
  private State myState = new State();

  public static ExternalProjectsDataStorage getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalProjectsDataStorage.class);
  }

  public ExternalProjectsDataStorage(@NotNull Project project) {
    myProject = project;
    myAlarm = new Alarm(Alarm.ThreadToUse.POOLED_THREAD, myProject);
  }

  public synchronized void load() {
    myExternalRootProjects.clear();
    try {
      final Collection<InternalExternalProjectInfo> projectInfos = load(myProject);
      for (InternalExternalProjectInfo projectInfo : projectInfos) {
        if (validate(projectInfo)) {
          myExternalRootProjects.put(
            Pair.create(projectInfo.getProjectSystemId(), new File(projectInfo.getExternalProjectPath())), projectInfo);
        }
      }
    }
    catch (IOException e) {
      LOG.debug(e);
    }

    mergeLocalSettings();
  }

  private static boolean validate(InternalExternalProjectInfo externalProjectInfo) {
    try {
      final DataNode<ProjectData> projectStructure = externalProjectInfo.getExternalProjectStructure();
      if (projectStructure == null) return false;

      ProjectDataManager.getInstance().ensureTheDataIsReadyToUse(projectStructure);
      return externalProjectInfo.getExternalProjectPath().equals(projectStructure.getData().getLinkedExternalProjectPath());
    }
    catch (Exception e) {
      LOG.warn(e);
    }
    return false;
  }

  @Override
  public synchronized void save() {
    if (!changed.compareAndSet(true, false)) return;

    myAlarm.cancelAllRequests();
    myAlarm.addRequest(new MySaveTask(myProject, myExternalRootProjects.values()), 0);
  }

  synchronized void update(@NotNull ExternalProjectInfo externalProjectInfo) {
    restoreInclusionSettings(externalProjectInfo.getExternalProjectStructure());

    final ProjectSystemId projectSystemId = externalProjectInfo.getProjectSystemId();
    final String projectPath = externalProjectInfo.getExternalProjectPath();
    DataNode<ProjectData> externalProjectStructure = externalProjectInfo.getExternalProjectStructure();
    long lastSuccessfulImportTimestamp = externalProjectInfo.getLastSuccessfulImportTimestamp();
    long lastImportTimestamp = externalProjectInfo.getLastImportTimestamp();

    final Pair<ProjectSystemId, File> key = Pair.create(projectSystemId, new File(projectPath));
    final InternalExternalProjectInfo old = myExternalRootProjects.get(key);
    if (old != null) {
      lastImportTimestamp = externalProjectInfo.getLastImportTimestamp();
      if (lastSuccessfulImportTimestamp == -1) {
        lastSuccessfulImportTimestamp = old.getLastSuccessfulImportTimestamp();
      }
      if (externalProjectInfo.getExternalProjectStructure() == null) {
        externalProjectStructure = old.getExternalProjectStructure();
      }
      else {
        externalProjectStructure = externalProjectInfo.getExternalProjectStructure().graphCopy();
      }
    }
    else {
      externalProjectStructure = externalProjectStructure != null ? externalProjectStructure.graphCopy() : null;
    }

    InternalExternalProjectInfo merged = new InternalExternalProjectInfo(
      projectSystemId,
      projectPath,
      externalProjectStructure
    );
    merged.setLastImportTimestamp(lastImportTimestamp);
    merged.setLastSuccessfulImportTimestamp(lastSuccessfulImportTimestamp);
    myExternalRootProjects.put(key, merged);

    changed.set(true);
  }

  synchronized void restoreInclusionSettings(@Nullable DataNode<ProjectData> projectDataNode) {
    if (projectDataNode == null) return;
    final String rootProjectPath = projectDataNode.getData().getLinkedExternalProjectPath();
    final ProjectState projectState = myState.map.get(rootProjectPath);
    if (projectState == null) return;

    ExternalSystemApiUtil.visit(projectDataNode, node -> {
      final DataNode<ExternalConfigPathAware> projectOrModuleNode = resolveProjectNode(node);
      assert projectOrModuleNode != null;
      final ModuleState moduleState = projectState.map.get(projectOrModuleNode.getData().getLinkedExternalProjectPath());
      node.setIgnored(isIgnored(projectState, moduleState, node.getKey()));
    });
  }

  synchronized void saveInclusionSettings(@Nullable DataNode<ProjectData> projectDataNode) {
    if (projectDataNode == null) return;

    final MultiMap<String, String> inclusionMap = MultiMap.create();
    final MultiMap<String, String> exclusionMap = MultiMap.create();
    ExternalSystemApiUtil.visit(projectDataNode, dataNode -> {
      try {
        dataNode.getDataBytes();
        DataNode<ExternalConfigPathAware> projectNode = resolveProjectNode(dataNode);
        if (projectNode != null) {
          final String projectPath = projectNode.getData().getLinkedExternalProjectPath();
          if (projectNode.isIgnored() || dataNode.isIgnored()) {
            exclusionMap.putValue(projectPath, dataNode.getKey().getDataType());
          }
          else {
            inclusionMap.putValue(projectPath, dataNode.getKey().getDataType());
          }
        }
      }
      catch (IOException e) {
        dataNode.clear(true);
      }
    });
    final MultiMap<String, String> map;
    ProjectState projectState = new ProjectState();
    if (inclusionMap.size() < exclusionMap.size()) {
      projectState.isInclusion = true;
      map = inclusionMap;
    }
    else {
      projectState.isInclusion = false;
      map = exclusionMap;
    }

    for (String path : map.keySet()) {
      projectState.map.put(path, new ModuleState(map.get(path)));
    }

    myState.map.put(projectDataNode.getData().getLinkedExternalProjectPath(), projectState);
    changed.set(true);
  }

  @Nullable
  synchronized ExternalProjectInfo get(@NotNull ProjectSystemId projectSystemId, @NotNull String externalProjectPath) {
    return myExternalRootProjects.get(Pair.create(projectSystemId, new File(externalProjectPath)));
  }

  synchronized void remove(@NotNull ProjectSystemId projectSystemId, @NotNull String externalProjectPath) {
    final InternalExternalProjectInfo removed = myExternalRootProjects.remove(Pair.create(projectSystemId, new File(externalProjectPath)));
    if(removed != null) {
      changed.set(true);
    }
  }

  @NotNull
  synchronized Collection<ExternalProjectInfo> list(@NotNull final ProjectSystemId projectSystemId) {
    return ContainerUtil.mapNotNull(myExternalRootProjects.values(),
                                    info -> projectSystemId.equals(info.getProjectSystemId()) ? info : null);
  }

  private void mergeLocalSettings() {
    for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
      final ProjectSystemId systemId = manager.getSystemId();

      AbstractExternalSystemLocalSettings settings = manager.getLocalSettingsProvider().fun(myProject);
      final Map<ExternalProjectPojo, Collection<ExternalProjectPojo>> availableProjects = settings.getAvailableProjects();
      final Map<String, Collection<ExternalTaskPojo>> availableTasks = settings.getAvailableTasks();

      for (Map.Entry<ExternalProjectPojo, Collection<ExternalProjectPojo>> entry : availableProjects.entrySet()) {
        final ExternalProjectPojo projectPojo = entry.getKey();
        final String externalProjectPath = projectPojo.getPath();
        final Pair<ProjectSystemId, File> key = Pair.create(systemId, new File(externalProjectPath));
        InternalExternalProjectInfo externalProjectInfo = myExternalRootProjects.get(key);
        if (externalProjectInfo == null) {
          final DataNode<ProjectData> dataNode = convert(systemId, projectPojo, entry.getValue(), availableTasks);
          externalProjectInfo = new InternalExternalProjectInfo(systemId, externalProjectPath, dataNode);
          myExternalRootProjects.put(key, externalProjectInfo);

          changed.set(true);
        }

        // restore linked project sub-modules
        ExternalProjectSettings linkedProjectSettings =
          manager.getSettingsProvider().fun(myProject).getLinkedProjectSettings(externalProjectPath);
        if (linkedProjectSettings != null && ContainerUtil.isEmpty(linkedProjectSettings.getModules())) {

          final Set<String> modulePaths = ContainerUtil.map2Set(
            ExternalSystemApiUtil.findAllRecursively(externalProjectInfo.getExternalProjectStructure(), ProjectKeys.MODULE),
            node -> node.getData().getLinkedExternalProjectPath());
          linkedProjectSettings.setModules(modulePaths);
        }
      }
    }
  }

  private static DataNode<ProjectData> convert(@NotNull ProjectSystemId systemId,
                                               @NotNull ExternalProjectPojo rootProject,
                                               @NotNull Collection<ExternalProjectPojo> childProjects,
                                               @NotNull Map<String, Collection<ExternalTaskPojo>> availableTasks) {
    ProjectData projectData = new ProjectData(systemId, rootProject.getName(), rootProject.getPath(), rootProject.getPath());
    DataNode<ProjectData> projectDataNode = new DataNode<ProjectData>(PROJECT, projectData, null);

    for (ExternalProjectPojo childProject : childProjects) {
      String moduleConfigPath = childProject.getPath();
      ModuleData moduleData = new ModuleData(childProject.getName(), systemId,
                                             ModuleTypeId.JAVA_MODULE, childProject.getName(),
                                             moduleConfigPath, moduleConfigPath);
      final DataNode<ModuleData> moduleDataNode = projectDataNode.createChild(MODULE, moduleData);

      final Collection<ExternalTaskPojo> moduleTasks = availableTasks.get(moduleConfigPath);
      if (moduleTasks != null) {
        for (ExternalTaskPojo moduleTask : moduleTasks) {
          TaskData taskData = new TaskData(systemId, moduleTask.getName(), moduleConfigPath, moduleTask.getDescription());
          moduleDataNode.createChild(TASK, taskData);
        }
      }
    }

    return projectDataNode;
  }

  private static void doSave(@NotNull final Project project, @NotNull Collection<InternalExternalProjectInfo> externalProjects)
    throws IOException {
    final File projectConfigurationFile = getProjectConfigurationFile(project);
    if (!FileUtil.createParentDirs(projectConfigurationFile)) {
      throw new IOException("Unable to save " + projectConfigurationFile);
    }

    for (Iterator<InternalExternalProjectInfo> iterator = externalProjects.iterator(); iterator.hasNext(); ) {
      InternalExternalProjectInfo externalProject = iterator.next();
      if (!validate(externalProject)) {
        iterator.remove();
        continue;
      }

      ExternalSystemApiUtil.visit(externalProject.getExternalProjectStructure(), dataNode -> {
        try {
          dataNode.getDataBytes();
        }
        catch (IOException e) {
          dataNode.clear(true);
        }
      });
    }

    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(projectConfigurationFile)));
    try {
      out.writeUTF(STORAGE_VERSION);
      out.writeInt(externalProjects.size());
      ObjectOutputStream os = new ObjectOutputStream(out);
      try {
        for (InternalExternalProjectInfo externalProject : externalProjects) {
          os.writeObject(externalProject);
        }
      }
      finally {
        os.close();
      }
    }
    finally {
      out.close();
    }
  }

  @SuppressWarnings("unchecked")
  @Nullable
  private static DataNode<ExternalConfigPathAware> resolveProjectNode(@NotNull DataNode node) {
    if ((MODULE.equals(node.getKey()) || PROJECT.equals(node.getKey())) && node.getData() instanceof ExternalConfigPathAware) {
      return (DataNode<ExternalConfigPathAware>)node;
    }
    DataNode parent = ExternalSystemApiUtil.findParent(node, MODULE);
    if (parent == null) {
      parent = ExternalSystemApiUtil.findParent(node, PROJECT);
    }
    return parent;
  }

  @NotNull
  private static Collection<InternalExternalProjectInfo> load(@NotNull Project project) throws IOException {
    SmartList<InternalExternalProjectInfo> projects = new SmartList<InternalExternalProjectInfo>();
    @SuppressWarnings("unchecked") final File configurationFile = getProjectConfigurationFile(project);
    if (!configurationFile.isFile()) return projects;

    DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(configurationFile)));

    try {
      final String storage_version = in.readUTF();
      if (!STORAGE_VERSION.equals(storage_version)) return projects;
      final int size = in.readInt();

      ObjectInputStream os = new ObjectInputStream(in);
      try {
        for (int i = 0; i < size; i++) {
          //noinspection unchecked
          InternalExternalProjectInfo projectDataDataNode = (InternalExternalProjectInfo)os.readObject();
          projects.add(projectDataDataNode);
        }
      }
      catch (Exception e) {
        throw new IOException(e);
      }
      finally {
        os.close();
      }
    }
    finally {
      in.close();
    }
    return projects;
  }

  private static File getProjectConfigurationFile(@NotNull Project project) {
    return new File(getProjectConfigurationDir(), project.getLocationHash() + "/project.dat");
  }

  private static File getProjectConfigurationDir() {
    return getExternalBuildSystemDir("Projects");
  }

  private static File getExternalBuildSystemDir(String folder) {
    return new File(PathManager.getSystemPath(), "external_build_system" + "/" + folder).getAbsoluteFile();
  }

  @Nullable
  @Override
  public synchronized State getState() {
    return myState;
  }

  @Override
  public synchronized void loadState(State state) {
    myState = state == null ? new State() : state;
  }

  synchronized void setIgnored(@NotNull final DataNode<?> dataNode, final boolean isIgnored) {
    //noinspection unchecked
    final DataNode<ProjectData> projectDataNode =
      PROJECT.equals(dataNode.getKey()) ? (DataNode<ProjectData>)dataNode : ExternalSystemApiUtil.findParent(dataNode, PROJECT);
    if (projectDataNode == null) {
      return;
    }

    ExternalSystemApiUtil.visit(dataNode, node -> node.setIgnored(isIgnored));

    saveInclusionSettings(projectDataNode);
  }

  synchronized boolean isIgnored(@NotNull String rootProjectPath, @NotNull String modulePath, @NotNull Key key) {
    final ProjectState projectState = myState.map.get(rootProjectPath);
    if (projectState == null) return false;

    final ModuleState moduleState = projectState.map.get(modulePath);
    return isIgnored(projectState, moduleState, key);
  }

  private static boolean isIgnored(@NotNull ProjectState projectState, @Nullable ModuleState moduleState, @NotNull Key<?> key) {
    return projectState.isInclusion ^ (moduleState != null && moduleState.set.contains(key.getDataType()));
  }

  static class State {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundValueWithTag = false, surroundKeyWithTag = false,
      keyAttributeName = "path", entryTagName = "projectState")
    public Map<String, ProjectState> map = ContainerUtil.newConcurrentMap();
  }

  static class ProjectState {
    @Property(surroundWithTag = false)
    @MapAnnotation(surroundWithTag = false, surroundValueWithTag = false, surroundKeyWithTag = false,
      keyAttributeName = "path", entryTagName = "dataType")
    public Map<String, ModuleState> map = ContainerUtil.newConcurrentMap();
    public boolean isInclusion;
  }

  static class ModuleState {
    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false, elementTag = "id")
    public Set<String> set = ContainerUtil.newConcurrentSet();

    public ModuleState() {
    }

    public ModuleState(Collection<String> values) {
      set.addAll(values);
    }
  }

  private static class MySaveTask implements Runnable {
    private Project myProject;
    private Collection<InternalExternalProjectInfo> myExternalProjects;

    public MySaveTask(Project project, Collection<InternalExternalProjectInfo> externalProjects) {
      myProject = project;
      myExternalProjects = ContainerUtil.map(externalProjects, info -> (InternalExternalProjectInfo)info.copy());
    }

    @Override
    public void run() {
      try {
        doSave(myProject, myExternalProjects);
      }
      catch (IOException e) {
        LOG.debug(e);
      }
    }
  }
}
