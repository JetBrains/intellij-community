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

import com.intellij.compiler.actions.ArtifactAwareProjectSettingsService;
import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.util.projectWizard.JdkChooserPanel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class IdeaProjectSettingsService extends ProjectSettingsService implements ArtifactAwareProjectSettingsService {
  private final Project myProject;

  public IdeaProjectSettingsService(final Project project) {
    myProject = project;
  }

  @Override
  public void openProjectSettings() {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
      @Override
      public void run() {
        config.selectProjectGeneralSettings(true);
      }
    });
  }

  @Override
  public void openGlobalLibraries() {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
      @Override
      public void run() {
        config.selectGlobalLibraries(true);
      }
    });
  }

  @Override
  public void openLibrary(@NotNull final Library library) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
      @Override
      public void run() {
        config.selectProjectOrGlobalLibrary(library, true);
      }
    });
  }

  @Override
  public boolean canOpenModuleSettings() {
    return true;
  }

  @Override
  public void openModuleSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), null);
  }

  @Override
  public boolean canOpenModuleLibrarySettings() {
    return true;
  }

  @Override
  public void openModuleLibrarySettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), ClasspathEditor.NAME);
  }

  @Override
  public boolean canOpenContentEntriesSettings() {
    return true;
  }

  @Override
  public void openContentEntriesSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), CommonContentEntriesEditor.NAME);
  }

  @Override
  public boolean canOpenModuleDependenciesSettings() {
    return true;
  }

  @Override
  public void openModuleDependenciesSettings(@NotNull final Module module, @Nullable final OrderEntry orderEntry) {
    ShowSettingsUtil.getInstance().editConfigurable(myProject, ProjectStructureConfigurable.getInstance(myProject), new Runnable() {
      @Override
      public void run() {
        ProjectStructureConfigurable.getInstance(myProject).selectOrderEntry(module, orderEntry);
      }
    });
  }

  @Override
  public boolean canOpenLibraryOrSdkSettings(OrderEntry orderEntry) {
    return true;
  }

  @Override
  public void openLibraryOrSdkSettings(@NotNull final OrderEntry orderEntry) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, new Runnable() {
      @Override
      public void run() {
        if (orderEntry instanceof JdkOrderEntry) {
          config.select(((JdkOrderEntry)orderEntry).getJdk(), true);
        } else {
          config.select((LibraryOrderEntry)orderEntry, true);
        }
      }
    });
  }

  @Override
  public boolean processModulesMoved(final Module[] modules, @Nullable final ModuleGroup targetGroup) {
    final ModuleStructureConfigurable rootConfigurable = ModuleStructureConfigurable.getInstance(myProject);
    if (rootConfigurable.updateProjectTree(modules, targetGroup)) { //inside project root editor
      if (targetGroup != null) {
        rootConfigurable.selectNodeInTree(targetGroup.toString());
      }
      else {
        rootConfigurable.selectNodeInTree(modules[0].getName());
      }
      return true;
    }
    return false;
  }

  @Override
  public void showModuleConfigurationDialog(String moduleToSelect, String editorNameToSelect) {
    ModulesConfigurator.showDialog(myProject, moduleToSelect, editorNameToSelect);
  }

  @Override
  public Sdk chooseAndSetSdk() {
    return JdkChooserPanel.chooseAndSetJDK(myProject);
  }

  @Override
  public void openArtifactSettings(@Nullable Artifact artifact) {
    ModulesConfigurator.showArtifactSettings(myProject, artifact);
  }
}
