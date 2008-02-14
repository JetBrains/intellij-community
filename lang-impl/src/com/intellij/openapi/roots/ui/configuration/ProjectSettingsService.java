package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
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
}
