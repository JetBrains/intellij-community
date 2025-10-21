// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.compiler.actions.ArtifactAwareProjectSettingsService;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ModuleStructureConfigurable;
import com.intellij.packaging.artifacts.Artifact;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class IdeaProjectSettingsService extends ProjectSettingsService implements ArtifactAwareProjectSettingsService {
  private final Project myProject;

  public IdeaProjectSettingsService(final Project project) {
    myProject = project;
  }

  @Override
  public void openProjectSettings() {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, () -> config.selectProjectGeneralSettings(true));
  }

  @Override
  public void openGlobalLibraries() {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, () -> config.selectGlobalLibraries(true));
  }

  @Override
  public void openLibrary(final @NotNull Library library) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, () -> config.selectProjectOrGlobalLibrary(library, true));
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
    ModulesConfigurator.showDialog(myProject, module.getName(), ClasspathEditor.getName());
  }

  @Override
  public boolean canOpenContentEntriesSettings() {
    return true;
  }

  @Override
  public void openContentEntriesSettings(final Module module) {
    ModulesConfigurator.showDialog(myProject, module.getName(), CommonContentEntriesEditor.getName());
  }

  @Override
  public boolean canOpenModuleDependenciesSettings() {
    return true;
  }

  @Override
  public void openModuleDependenciesSettings(final @NotNull Module module, final @Nullable OrderEntry orderEntry) {
    ShowSettingsUtil.getInstance().editConfigurable(myProject, ProjectStructureConfigurable.getInstance(myProject), () -> ProjectStructureConfigurable.getInstance(myProject).selectOrderEntry(module, orderEntry));
  }

  @Override
  public boolean canOpenLibraryOrSdkSettings(OrderEntry orderEntry) {
    return true;
  }

  @Override
  public void openLibraryOrSdkSettings(final @NotNull OrderEntry orderEntry) {
    final ProjectStructureConfigurable config = ProjectStructureConfigurable.getInstance(myProject);
    ShowSettingsUtil.getInstance().editConfigurable(myProject, config, () -> {
      if (orderEntry instanceof JdkOrderEntry) {
        config.select(((JdkOrderEntry)orderEntry).getJdk(), true);
      } else {
        config.select((LibraryOrderEntry)orderEntry, true);
      }
    });
  }

  @Override
  public boolean processModulesMoved(final Module[] modules, final @Nullable String targetGroupName) {
    final ModuleStructureConfigurable rootConfigurable = ProjectStructureConfigurable.getInstance(myProject).getModulesConfig();
    if (rootConfigurable.updateProjectTree(modules)) { //inside project root editor
      if (targetGroupName != null) {
        rootConfigurable.selectNodeInTree(targetGroupName);
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

  private Sdk myDeprecatedChosenSdk = null;

  /**
   * @deprecated Please use {@link SdkPopupFactory} instead.
   *
   * Many usages of that API are too bogus and do duplicate similar code all other the place.
   * It is not even possible to filter unneeded SDK types or SDK instances in the dialog.
   *
   * This method is no longer supported and behaves a bit broken: the first call returns {@code null},
   * the second call may return a chosen SDK from the first call (only once). This is the way to
   * avoid breaking the older code scenarios.
   */
  @Override
  @Deprecated(forRemoval = true)
  public Sdk chooseAndSetSdk() {
    Logger
      .getInstance(getClass())
      .error("Call to the deprecated ProjectSettingsService#chooseAndSetSdk method. Please use new API instead");

    if (myDeprecatedChosenSdk != null) {
      Sdk chosenSdk = myDeprecatedChosenSdk;
      myDeprecatedChosenSdk = null;
      return chosenSdk;
    }

    SdkPopupFactory
      .newBuilder()
      .withProject(myProject)
      .withSdkType(JavaSdk.getInstance())
      .updateProjectSdkFromSelection()
      .onSdkSelected(sdk -> myDeprecatedChosenSdk = sdk)
      .buildPopup()
      .showInFocusCenter();

    return null;
  }

  @Override
  public void openArtifactSettings(@Nullable Artifact artifact) {
    ModulesConfigurator.showArtifactSettings(myProject, artifact);
  }
}
