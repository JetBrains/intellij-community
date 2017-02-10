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
package com.intellij.framework.detection.impl.exclude.old;

import com.intellij.framework.detection.impl.exclude.ExcludedFileState;
import com.intellij.framework.detection.impl.exclude.ExcludesConfigurationState;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author nik
 */
@State(name = "FacetAutodetectingManager")
public class OldFacetDetectionExcludesConfiguration implements PersistentStateComponent<DisabledAutodetectionInfo> {
  @NonNls public static final String COMPONENT_NAME = "FacetAutodetectingManager";
  private DisabledAutodetectionInfo myDisabledAutodetectionInfo;
  private final Project myProject;

  public static OldFacetDetectionExcludesConfiguration getInstance(Project project) {
    return ServiceManager.getService(project, OldFacetDetectionExcludesConfiguration.class);
  }

  public OldFacetDetectionExcludesConfiguration(Project project) {
    myProject = project;
  }

  @Override
  public DisabledAutodetectionInfo getState() {
    return myDisabledAutodetectionInfo;
  }

  @Override
  public void loadState(final DisabledAutodetectionInfo state) {
    myDisabledAutodetectionInfo = state;
  }

  @Nullable
  public ExcludesConfigurationState convert() {
    if (myDisabledAutodetectionInfo == null || myDisabledAutodetectionInfo.getElements().isEmpty()) {
      return null;
    }

    final ExcludesConfigurationState state = new ExcludesConfigurationState();
    for (DisabledAutodetectionByTypeElement element : myDisabledAutodetectionInfo.getElements()) {
      final String frameworkId = element.getFacetTypeId();
      final List<DisabledAutodetectionInModuleElement> moduleElements = element.getModuleElements();
      if (moduleElements.isEmpty()) {
        state.getFrameworkTypes().add(frameworkId);
        continue;
      }
      Set<String> excludedUrls = new LinkedHashSet<>();
      for (DisabledAutodetectionInModuleElement moduleElement : moduleElements) {
        if (moduleElement.isDisableInWholeModule()) {
          final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleElement.getModuleName());
          if (module != null) {
            Collections.addAll(excludedUrls, ModuleRootManager.getInstance(module).getContentRootUrls());
          }
        }
        else {
          excludedUrls.addAll(moduleElement.getFiles());
          excludedUrls.addAll(moduleElement.getDirectories());
        }
      }
      for (String url : excludedUrls) {
        state.getFiles().add(new ExcludedFileState(url, frameworkId));
      }
    }
    return state;
  }
}
