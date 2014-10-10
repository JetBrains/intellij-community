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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.SettingsSavingComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.internal.InternalExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ConcurrentHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Vladislav.Soroka
 * @since 9/18/2014
 */
public class ExternalProjectsDataStorage implements SettingsSavingComponent {
  private static final Logger LOG = Logger.getInstance(ExternalProjectsDataStorage.class);

  @NotNull
  private final Project myProject;
  @NotNull
  private final Map<Pair<ProjectSystemId, String>, InternalExternalProjectInfo> myExternalRootProjects =
    new ConcurrentHashMap<Pair<ProjectSystemId, String>, InternalExternalProjectInfo>();

  public static ExternalProjectsDataStorage getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ExternalProjectsDataStorage.class);
  }

  public ExternalProjectsDataStorage(@NotNull Project project) {
    myProject = project;
  }

  public void load() {
    // TODO [Vlad] load data for the opened project
  }

  @Override
  public void save() {
    // TODO [Vlad] save data if changed
  }

  void add(@NotNull ExternalProjectInfo externalProjectInfo) {
    final ProjectSystemId projectSystemId = externalProjectInfo.getProjectSystemId();
    final String projectPath = externalProjectInfo.getExternalProjectPath();
    DataNode<ProjectData> externalProjectStructure = externalProjectInfo.getExternalProjectStructure();
    long lastSuccessfulImportTimestamp = externalProjectInfo.getLastSuccessfulImportTimestamp();
    long lastImportTimestamp = externalProjectInfo.getLastImportTimestamp();

    final Pair<ProjectSystemId, String> key = Pair.create(projectSystemId, projectPath);
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
      externalProjectStructure
    );
    merged.setLastImportTimestamp(lastImportTimestamp);
    merged.setLastSuccessfulImportTimestamp(lastSuccessfulImportTimestamp);
    myExternalRootProjects.put(key, merged);
  }

  @Nullable
  ExternalProjectInfo get(@NotNull ProjectSystemId projectSystemId, @NotNull String externalProjectPath) {
    return myExternalRootProjects.get(Pair.create(projectSystemId, externalProjectPath));
  }
}
