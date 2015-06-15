/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.execution.ExternalTaskPojo;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
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
import com.intellij.util.Consumer;
import com.intellij.util.Function;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Vladislav.Soroka
 * @since 9/18/2014
 */
public class ExternalProjectsDataStorage implements SettingsSavingComponent {
  private static final Logger LOG = Logger.getInstance(ExternalProjectsDataStorage.class);

  private static final String STORAGE_VERSION = ExternalProjectsDataStorage.class.getSimpleName() + ".1";

  @NotNull
  private final Project myProject;
  @NotNull
  private final Map<Pair<ProjectSystemId, File>, InternalExternalProjectInfo> myExternalRootProjects =
    ContainerUtil.newConcurrentMap(ExternalSystemUtil.HASHING_STRATEGY);

  private final AtomicBoolean changed = new AtomicBoolean();

  public static ExternalProjectsDataStorage getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalProjectsDataStorage.class);
  }

  public ExternalProjectsDataStorage(@NotNull Project project) {
    myProject = project;
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

    try {
      doSave(myProject, myExternalRootProjects.values());
    }
    catch (IOException e) {
      LOG.debug(e);
    }
  }

  synchronized void update(@NotNull ExternalProjectInfo externalProjectInfo) {
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
    }

    InternalExternalProjectInfo merged = new InternalExternalProjectInfo(
      projectSystemId,
      projectPath,
      externalProjectStructure != null ? externalProjectStructure.graphCopy() : null
    );
    merged.setLastImportTimestamp(lastImportTimestamp);
    merged.setLastSuccessfulImportTimestamp(lastSuccessfulImportTimestamp);
    myExternalRootProjects.put(key, merged);

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
    return ContainerUtil.mapNotNull(myExternalRootProjects.values(), new Function<InternalExternalProjectInfo, ExternalProjectInfo>() {
      @Override
      public ExternalProjectInfo fun(InternalExternalProjectInfo info) {
        return projectSystemId.equals(info.getProjectSystemId()) ? info : null;
      }
    });
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
            new Function<DataNode<ModuleData>, String>() {
              @Override
              public String fun(DataNode<ModuleData> node) {
                return node.getData().getLinkedExternalProjectPath();
              }
            });
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
    DataNode<ProjectData> projectDataNode = new DataNode<ProjectData>(ProjectKeys.PROJECT, projectData, null);

    for (ExternalProjectPojo childProject : childProjects) {
      String moduleConfigPath = childProject.getPath();
      ModuleData moduleData = new ModuleData(childProject.getName(), systemId,
                                             ModuleTypeId.JAVA_MODULE, childProject.getName(),
                                             moduleConfigPath, moduleConfigPath);
      final DataNode<ModuleData> moduleDataNode = projectDataNode.createChild(ProjectKeys.MODULE, moduleData);

      final Collection<ExternalTaskPojo> moduleTasks = availableTasks.get(moduleConfigPath);
      if (moduleTasks != null) {
        for (ExternalTaskPojo moduleTask : moduleTasks) {
          TaskData taskData = new TaskData(systemId, moduleTask.getName(), moduleConfigPath, moduleTask.getDescription());
          moduleDataNode.createChild(ProjectKeys.TASK, taskData);
        }
      }
    }

    return projectDataNode;
  }

  private static void doSave(@NotNull Project project, @NotNull Collection<InternalExternalProjectInfo> externalProjects) throws IOException {
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

      ExternalSystemApiUtil.visit(externalProject.getExternalProjectStructure(), new Consumer<DataNode>() {
        @Override
        public void consume(DataNode dataNode) {
          try {
            dataNode.getDataBytes();
          }
          catch (IOException e) {
            dataNode.clear(true);
          }
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

  @NotNull
  private static Collection<InternalExternalProjectInfo> load(@NotNull Project project) throws IOException {
    SmartList<InternalExternalProjectInfo> projects = new SmartList<InternalExternalProjectInfo>();
    final File configurationFile = getProjectConfigurationFile(project);
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
      catch (ClassNotFoundException e) {
        IOException ioException = new IOException();
        ioException.initCause(e);
        throw ioException;
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
}
