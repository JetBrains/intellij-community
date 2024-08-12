// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.ide.projectView.impl.ModuleGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.PersistentLibraryKind;
import com.intellij.util.concurrency.annotations.RequiresBlockingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProjectSettingsService {
  @RequiresBlockingContext
  public static ProjectSettingsService getInstance(Project project) {
    return project.getService(ProjectSettingsService.class);
  }

  public void openProjectSettings() {
  }

  public void openGlobalLibraries() {
  }

  public void openLibrary(@NotNull Library library) {
  }

  public void openModuleSettings(final Module module) {
  }

  public boolean canOpenModuleSettings() {
    return false;
  }

  public void openModuleLibrarySettings(final Module module) {
  }

  public boolean canOpenModuleLibrarySettings() {
    return false;
  }

  public void openContentEntriesSettings(final Module module) {
  }

  public boolean canOpenContentEntriesSettings() {
    return false;
  }

  public void openModuleDependenciesSettings(@NotNull Module module, @Nullable OrderEntry orderEntry) {
  }

  public boolean canOpenModuleDependenciesSettings() {
    return false;
  }

  public void openLibraryOrSdkSettings(final @NotNull OrderEntry orderEntry) {
    Configurable additionalSettingsConfigurable = getLibrarySettingsConfigurable(orderEntry);
    if (additionalSettingsConfigurable != null) {
      ShowSettingsUtil.getInstance().showSettingsDialog(orderEntry.getOwnerModule().getProject(),
                                                        additionalSettingsConfigurable.getDisplayName());
    }
  }

  public boolean canOpenLibraryOrSdkSettings(final OrderEntry orderEntry) {
    return getLibrarySettingsConfigurable(orderEntry) != null;
  }

  private static @Nullable Configurable getLibrarySettingsConfigurable(OrderEntry orderEntry) {
    if (!(orderEntry instanceof LibraryOrderEntry libOrderEntry)) return null;
    Library lib = libOrderEntry.getLibrary();
    if (lib instanceof LibraryEx) {
      Project project = libOrderEntry.getOwnerModule().getProject();
      PersistentLibraryKind<?> libKind = ((LibraryEx)lib).getKind();
      if (libKind != null) {
        return LibrarySettingsProvider.getAdditionalSettingsConfigurable(project, libKind);
      }
    }
    return null;
  }

  public boolean processModulesMoved(final Module[] modules, final @Nullable ModuleGroup targetGroup) {
    return false;
  }

  public void showModuleConfigurationDialog(@Nullable String moduleToSelect, @Nullable String editorNameToSelect) {
  }

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
  @Deprecated
  public @Nullable Sdk chooseAndSetSdk() {
    Logger.getInstance(getClass()).warn("Call to the deprecated ProjectSettingsService#chooseAndSetSdk method. Please use new API instead");
    return null;
  }
}
