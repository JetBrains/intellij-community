// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.packaging.impl.artifacts;

import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.ArtifactModel;
import com.intellij.packaging.elements.ManifestFileProvider;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DefaultPackagingElementResolvingContext implements PackagingElementResolvingContext {
  private final Project myProject;
  private final DefaultModulesProvider myModulesProvider;

  public DefaultPackagingElementResolvingContext(Project project) {
    myProject = project;
    myModulesProvider = new DefaultModulesProvider(myProject);
  }

  @Override
  public @NotNull Project getProject() {
    return myProject;
  }

  @Override
  public @NotNull ArtifactModel getArtifactModel() {
    return ArtifactManager.getInstance(myProject);
  }

  @Override
  public @NotNull ModulesProvider getModulesProvider() {
    return myModulesProvider;
  }

  @Override
  public @NotNull FacetsProvider getFacetsProvider() {
    return DefaultFacetsProvider.INSTANCE;
  }

  @Override
  public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
    return findLibrary(myProject, level, libraryName);
  }

  @Override
  public @NotNull ManifestFileProvider getManifestFileProvider() {
    return new DefaultManifestFileProvider(this);
  }

  public static @Nullable Library findLibrary(Project project, String level, String libraryName) {
    LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, project);
    return table != null ? table.getLibraryByName(libraryName) : null;
  }
}
