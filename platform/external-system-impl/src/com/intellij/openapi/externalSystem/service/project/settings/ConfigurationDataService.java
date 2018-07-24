/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.settings;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.ConfigurationDataImpl;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.project.settings.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.externalSystem.service.project.manage.AbstractModuleDataService.MODULE_KEY;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
@Order(ExternalSystemConstants.BUILTIN_SERVICE_ORDER)
public class ConfigurationDataService extends AbstractProjectDataService<ConfigurationData, Void> {
  private static final Logger LOG = Logger.getInstance(ConfigurationDataService.class);
  public static final String EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED = "external.system.configuration.import.enabled";

  @NotNull
  @Override
  public Key<ConfigurationData> getTargetDataKey() {
    return ProjectKeys.CONFIGURATION;
  }

  @Override
  public void importData(@NotNull Collection<DataNode<ConfigurationData>> toImport,
                         @Nullable ProjectData projectData,
                         @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    if (toImport.isEmpty() || !Registry.is(EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED)) {
      LOG.debug("Configuration data is" + (!toImport.isEmpty() ? " not " : " ") + "empty, Registry flag is " + Registry.is(EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED));
      return;
    }

    final DataNode<ConfigurationData> javaProjectDataNode = toImport.iterator().next();
    final DataNode<ProjectData> projectDataNode = ExternalSystemApiUtil.findParent(javaProjectDataNode, ProjectKeys.PROJECT);

    assert projectDataNode != null;

    DataNode<ConfigurationData> projectConfigurationNode = ExternalSystemApiUtil.find(projectDataNode, ProjectKeys.CONFIGURATION);
    if (projectConfigurationNode != null) {

      final ConfigurationData data = projectConfigurationNode.getData();
      if (LOG.isDebugEnabled() && data instanceof ConfigurationDataImpl) {
        LOG.debug("Importing project configuration: " + ((ConfigurationDataImpl)data).getJsonString());
      }

      if (!ExternalSystemApiUtil.isOneToOneMapping(project, projectDataNode.getData())) {
        LOG.warn("This external project are not the only project in the current IDE workspace, " +
                 "found project level configuration can override the configuration came from other external projects.");
      }

      for (ConfigurationHandler handler : ConfigurationHandler.EP_NAME.getExtensions()) {
        handler.apply(project, modelsProvider, data);
      }
    }

    for (DataNode<ConfigurationData> node : toImport) {
      if (node == projectConfigurationNode) continue;

      DataNode<ModuleData> moduleDataNode = ExternalSystemApiUtil.findParent(node, ProjectKeys.MODULE);
      if (moduleDataNode != null) {
        Module module = moduleDataNode.getUserData(MODULE_KEY);
        module = module != null ? module : modelsProvider.findIdeModule(moduleDataNode.getData());

        if (module == null) {
          LOG.warn(String.format(
            "Can't import module level configuration. Reason: target module (%s) is not found at the ide", moduleDataNode));
          continue;
        }

        final ConfigurationData data = node.getData();
        if (LOG.isDebugEnabled() && data instanceof ConfigurationDataImpl) {
          LOG.debug("Importing module configuration: " + ((ConfigurationDataImpl)data).getJsonString());
        }

        for (ConfigurationHandler handler : ConfigurationHandler.EP_NAME.getExtensions()) {
          handler.apply(module, modelsProvider, data);
        }
      }
    }
  }
}