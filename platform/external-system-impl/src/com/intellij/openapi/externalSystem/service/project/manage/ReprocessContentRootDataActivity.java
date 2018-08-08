// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.intellij.openapi.externalSystem.model.ProjectKeys.CONTENT_ROOT;

public class ReprocessContentRootDataActivity implements StartupActivity, DumbAware {

  private static final Logger LOG = Logger.getInstance(ReprocessContentRootDataActivity.class);

  @Override
  public void runActivity(@NotNull Project project) {

    final ProjectDataManager dataManager = ProjectDataManager.getInstance();
    final ContentRootDataService service = new ContentRootDataService();
    final IdeModifiableModelsProviderImpl modifiableModelsProvider = new IdeModifiableModelsProviderImpl(project);

    logUnitTest("Adding 'reprocess content root data' activity to 'runWhenSmart' queue in project [hash=" + project.hashCode() + "]");
    ApplicationManager.getApplication().invokeLater(() -> {
      logUnitTest("Reprocessing content root data for project [hash=" + project.hashCode() + "]");
      ExternalProjectsManagerImpl.getInstance(project).init();
      final boolean haveModulesToProcess = ModuleManager.getInstance(project).getModules().length > 0;
      if (!haveModulesToProcess) {
        logUnitTest("Have zero modules to process, returning");
        return;
      }
      try {
        final Collection<ExternalSystemManager<?, ?, ?, ?, ?>> managers = ExternalSystemApiUtil.getAllManagers();
        logUnitTest("Found [" + managers.size() + "] external system managers");
        for (ExternalSystemManager<?, ?, ?, ?, ?> manager : managers) {
          ProjectSystemId id = manager.getSystemId();
          final Collection<ExternalProjectInfo> data = dataManager.getExternalProjectsData(project, id);
          logUnitTest("Found [" + data.size() + "] external project infos using manager class=[" + dataManager.getClass().getCanonicalName() + "]");
          for (ExternalProjectInfo info : data) {
            DataNode<ProjectData> projectStructure = info.getExternalProjectStructure();
            logUnitTest("External data graph root is "
                        + (projectStructure == null ? "" : "not")
                        + " null for external project path=[" + info.getExternalProjectPath() + "]");
            if (projectStructure != null) {
              Collection<DataNode<ContentRootData>> roots = ExternalSystemApiUtil.findAllRecursively(projectStructure, CONTENT_ROOT);
              service.importData(roots, null, project, modifiableModelsProvider);
            }
          }
        }
      } finally {
        ExternalSystemApiUtil.doWriteAction(() -> modifiableModelsProvider.commit());
      }
    }, project.getDisposed());
  }

  protected void logUnitTest(String message) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.info(message);
    }
  }
}
