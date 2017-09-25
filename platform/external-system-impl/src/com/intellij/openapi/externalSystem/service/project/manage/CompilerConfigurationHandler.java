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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.CompilerConfigurationImpl;
import com.intellij.compiler.CompilerWorkspaceConfiguration;
import com.intellij.compiler.options.CompilerUIConfigurable;
import com.intellij.compiler.server.BuildManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.ConfigurationData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;

/**
 * @author Vladislav.Soroka
 */
public class CompilerConfigurationHandler implements ConfigurationHandler {

  private static final Logger LOG = Logger.getInstance(CompilerConfigurationHandler.class);

  @Override
  public void apply(@NotNull Project project,
                    @NotNull IdeModifiableModelsProvider modelsProvider,
                    @NotNull ConfigurationData configuration) {
    Object obj = configuration.find("compiler");
    if (!(obj instanceof Map)) return;
    Map configurationMap = ((Map)obj);

    ApplicationManager.getApplication().invokeLater(() -> {
      final CompilerConfigurationImpl compilerConfiguration = (CompilerConfigurationImpl)CompilerConfiguration.getInstance(project);
      final CompilerWorkspaceConfiguration workspaceConfiguration = CompilerWorkspaceConfiguration.getInstance(project);

      boolean changed = false;
      String resourcePatterns = getString(configurationMap, "resourcePatterns");
      if (resourcePatterns != null) {
        try {
          String[] resourceFilePatterns = compilerConfiguration.getResourceFilePatterns();
          compilerConfiguration.removeResourceFilePatterns();
          CompilerUIConfigurable.applyResourcePatterns(resourcePatterns, compilerConfiguration);
          if (!Arrays.equals(resourceFilePatterns, compilerConfiguration.getResourceFilePatterns())) {
            changed = true;
          }
        }
        catch (ConfigurationException e) {
          LOG.warn("Unable to apply compiler resource patterns", e);
        }
      }

      Number processHeapSize = getNumber(configurationMap, "processHeapSize");
      if (processHeapSize != null && processHeapSize.intValue() != compilerConfiguration.getBuildProcessHeapSize(0)) {
        compilerConfiguration.setBuildProcessHeapSize(processHeapSize.intValue());
        changed = true;
      }
      Boolean autoShowFirstErrorInEditor = getBoolean(configurationMap, "autoShowFirstErrorInEditor");
      if (autoShowFirstErrorInEditor != null && workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR != autoShowFirstErrorInEditor) {
        workspaceConfiguration.AUTO_SHOW_ERRORS_IN_EDITOR = autoShowFirstErrorInEditor;
        changed = true;
      }
      Boolean displayNotificationPopup = getBoolean(configurationMap, "displayNotificationPopup");
      if (displayNotificationPopup != null && workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP != displayNotificationPopup) {
        workspaceConfiguration.DISPLAY_NOTIFICATION_POPUP = displayNotificationPopup;
        changed = true;
      }
      Boolean clearOutputDirectory = getBoolean(configurationMap, "clearOutputDirectory");
      if (clearOutputDirectory != null && workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY != clearOutputDirectory) {
        workspaceConfiguration.CLEAR_OUTPUT_DIRECTORY = clearOutputDirectory;
        changed = true;
      }
      Boolean addNotNullAssertions = getBoolean(configurationMap, "addNotNullAssertions");
      if (addNotNullAssertions != null && compilerConfiguration.isAddNotNullAssertions() != addNotNullAssertions) {
        compilerConfiguration.setAddNotNullAssertions(addNotNullAssertions);
        changed = true;
      }
      Boolean enableAutomake = getBoolean(configurationMap, "enableAutomake");
      if (enableAutomake != null && workspaceConfiguration.MAKE_PROJECT_ON_SAVE != enableAutomake) {
        workspaceConfiguration.MAKE_PROJECT_ON_SAVE = enableAutomake;
        changed = true;
      }
      Boolean parallelCompilation = getBoolean(configurationMap, "parallelCompilation");
      if (parallelCompilation != null && workspaceConfiguration.PARALLEL_COMPILATION != parallelCompilation) {
        workspaceConfiguration.PARALLEL_COMPILATION = parallelCompilation;
        changed = true;
      }
      Boolean rebuildOnDependencyChange = getBoolean(configurationMap, "rebuildModuleOnDependencyChange");
      if (rebuildOnDependencyChange != null && workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE != rebuildOnDependencyChange) {
        workspaceConfiguration.REBUILD_ON_DEPENDENCY_CHANGE = rebuildOnDependencyChange;
        changed = true;
      }
      String additionalVmOptions = getString(configurationMap, "additionalVmOptions");
      if (additionalVmOptions != null && workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS != additionalVmOptions) {
        workspaceConfiguration.COMPILER_PROCESS_ADDITIONAL_VM_OPTIONS = additionalVmOptions;
        changed = true;
      }

      if (changed) {
        BuildManager.getInstance().clearState(project);
      }
    });
  }

  @Nullable
  private static Boolean getBoolean(Map map, String key) {
    Object o = map.get(key);
    return o instanceof Boolean ? (Boolean)o : null;
  }

  private static Number getNumber(Map map, String key) {
    Object o = map.get(key);
    return o instanceof Number ? (Number)o : null;
  }

  @Nullable
  private static String getString(Map map, String key) {
    Object o = map.get(key);
    return o instanceof String ? (String)o : null;
  }
}
