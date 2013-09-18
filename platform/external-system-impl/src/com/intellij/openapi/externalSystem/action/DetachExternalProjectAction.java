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
package com.intellij.openapi.externalSystem.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalProjectPojo;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemRecentTasksList;
import com.intellij.openapi.externalSystem.service.task.ui.ExternalSystemTasksTreeModel;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 6/13/13 5:42 PM
 */
public class DetachExternalProjectAction extends AnAction implements DumbAware {

  public DetachExternalProjectAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.detach.external.project.text", "external"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.detach.external.project.description"));
    getTemplatePresentation().setIcon(SystemInfoRt.isMac ? AllIcons.ToolbarDecorator.Mac.Remove : AllIcons.ToolbarDecorator.Remove);
  }

  @Override
  public void update(AnActionEvent e) {
    ExternalActionUtil.MyInfo info = ExternalActionUtil.getProcessingInfo(e.getDataContext());
    e.getPresentation().setEnabled(info.externalProject != null);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    ExternalActionUtil.MyInfo info = ExternalActionUtil.getProcessingInfo(e.getDataContext());
    if (info.settings == null || info.localSettings == null || info.externalProject == null || info.ideProject == null
        || info.externalSystemId == null)
    {
      return;
    }
    
    e.getPresentation().setText(
      ExternalSystemBundle.message("action.detach.external.project.text",info.externalSystemId.getReadableName())
    );

    ExternalSystemTasksTreeModel allTasksModel = ExternalSystemDataKeys.ALL_TASKS_MODEL.getData(e.getDataContext());
    if (allTasksModel != null) {
      allTasksModel.pruneNodes(info.externalProject);
    }

    ExternalSystemRecentTasksList recentTasksList = ExternalSystemDataKeys.RECENT_TASKS_LIST.getData(e.getDataContext());
    if (recentTasksList != null) {
      recentTasksList.getModel().forgetTasksFrom(info.externalProject.getPath());
    }
    
    info.localSettings.forgetExternalProjects(Collections.singleton(info.externalProject.getPath()));
    info.settings.unlinkExternalProject(info.externalProject.getPath());

    // Process orphan modules.
    PlatformFacade platformFacade = ServiceManager.getService(PlatformFacade.class);
    String externalSystemIdAsString = info.externalSystemId.toString();
    List<Module> orphanModules = ContainerUtilRt.newArrayList();
    for (Module module : platformFacade.getModules(info.ideProject)) {
      String systemId = module.getOptionValue(ExternalSystemConstants.EXTERNAL_SYSTEM_ID_KEY);
      if (!externalSystemIdAsString.equals(systemId)) {
        continue;
      }
      String path = module.getOptionValue(ExternalSystemConstants.LINKED_PROJECT_PATH_KEY);
      if (info.externalProject.getPath().equals(path)) {
        orphanModules.add(module);
      }
    }

    if (!orphanModules.isEmpty()) {
      ExternalSystemUtil.ruleOrphanModules(orphanModules, info.ideProject, info.externalSystemId);
    }
  }
}
