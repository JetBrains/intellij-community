// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.ExternalSystemManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ContentRootData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProviderImpl;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.CONTENT_ROOT;

public class ReprocessContentRootDataActivity implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {

    final ProjectDataManager dataManager = ProjectDataManager.getInstance();
    final ContentRootDataService service = new ContentRootDataService();
    final IdeModifiableModelsProviderImpl modifiableModelsProvider = new IdeModifiableModelsProviderImpl(project);

    DumbService.getInstance(project).runWhenSmart(
        () -> {
          final boolean haveModulesToProcess = ModuleManager.getInstance(project).getModules().length > 0;
          if (!haveModulesToProcess) {
            return;
          }

          try {
            for (ExternalSystemManager<?, ?, ?, ?, ?> manager : ExternalSystemApiUtil.getAllManagers()) {
              ProjectSystemId id = manager.getSystemId();
              for (ExternalProjectInfo info : dataManager.getExternalProjectsData(project, id)) {
                DataNode<ProjectData> projectStructure = info.getExternalProjectStructure();
                if (projectStructure != null) {
                  Collection<DataNode<ContentRootData>> roots = ExternalSystemApiUtil.findAllRecursively(projectStructure, CONTENT_ROOT);
                  service.importData(roots, null, project, modifiableModelsProvider);
                }
              }
            }
          } finally {
            ExternalSystemApiUtil.doWriteAction(() -> modifiableModelsProvider.commit());
          }
        }
    );


  }
}
