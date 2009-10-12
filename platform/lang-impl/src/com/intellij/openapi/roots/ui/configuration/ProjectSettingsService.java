/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class ProjectSettingsService {
  public static ProjectSettingsService getInstance(Project project) {
    return ServiceManager.getService(project, ProjectSettingsService.class);
  }

  public void openModuleSettings(final Module module) {
  }

  public void openModuleLibrarySettings(final Module module) {
  }

  public void openContentEntriesSettings(final Module module) {
  }

  public void openProjectLibrarySettings(final NamedLibraryElement value) {
  }

  public boolean processModulesMoved(final Module[] modules, @Nullable final ModuleGroup targetGroup) {
    return false;
  }

  public void showModuleConfigurationDialog(@Nullable String moduleToSelect, @Nullable String tabNameToSelect, boolean showModuleWizard) {
  }

  public Sdk chooseAndSetSdk() {
    return null;
  }
}
