// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.externalSystem.project.ArtifactExternalDependenciesImporter;
import com.intellij.openapi.externalSystem.project.PackagingModifiableModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.ManifestFileProvider;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.DefaultManifestFileProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

public class PackagingModifiableModelImpl implements PackagingModifiableModel {

  private final Project myProject;
  private final IdeModifiableModelsProvider myModelsProvider;
  private ModifiableArtifactModel myModifiableArtifactModel;
  private MyPackagingElementResolvingContext myPackagingElementResolvingContext;
  private final ArtifactExternalDependenciesImporter myArtifactExternalDependenciesImporter;

  public PackagingModifiableModelImpl(@NotNull Project project, @NotNull IdeModifiableModelsProvider modifiableModelsProvider) {
    myProject = project;
    myModelsProvider = modifiableModelsProvider;
    myArtifactExternalDependenciesImporter = new ArtifactExternalDependenciesImporterImpl();
  }

  @Override
  public @NotNull ModifiableArtifactModel getModifiableArtifactModel() {
    if (myModifiableArtifactModel == null) {
      myModifiableArtifactModel = myModelsProvider instanceof IdeUIModifiableModelsProvider
                                  ?
                                  ((IdeUIModifiableModelsProvider)myModelsProvider).getModifiableArtifactModel()
                                  : doGetModifiableArtifactModel();
    }
    return myModifiableArtifactModel;
  }

  private ModifiableArtifactModel doGetModifiableArtifactModel() {
    return ReadAction.compute(() -> {
      ArtifactManager artifactManager = ArtifactManager.getInstance(myProject);
      return artifactManager != null ? artifactManager.createModifiableModel() : new DummyArtifactModel();
    });
  }

  @Override
  public @NotNull PackagingElementResolvingContext getPackagingElementResolvingContext() {
    if (myPackagingElementResolvingContext == null) {
      myPackagingElementResolvingContext = new MyPackagingElementResolvingContext();
    }
    return myPackagingElementResolvingContext;
  }

  @Override
  public @NotNull ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter() {
    return myArtifactExternalDependenciesImporter;
  }

  @Override
  public void commit() {
    myArtifactExternalDependenciesImporter.applyChanges(getModifiableArtifactModel(), getPackagingElementResolvingContext());
    if (myModifiableArtifactModel != null) {
      myModifiableArtifactModel.commit();
    }
  }

  @Override
  public void dispose() {
    if (myModifiableArtifactModel != null) {
      myModifiableArtifactModel.dispose();
    }
  }

  private class MyPackagingElementResolvingContext implements PackagingElementResolvingContext {
    private final ModulesProvider myModulesProvider = new MyModulesProvider();
    private final MyFacetsProvider myFacetsProvider = new MyFacetsProvider();
    private final ManifestFileProvider myManifestFileProvider = new DefaultManifestFileProvider(this);

    @Override
    public @NotNull Project getProject() {
      return myProject;
    }

    @Override
    public @NotNull ArtifactModel getArtifactModel() {
      return PackagingModifiableModelImpl.this.getModifiableArtifactModel();
    }

    @Override
    public @NotNull ModulesProvider getModulesProvider() {
      return myModulesProvider;
    }

    @Override
    public @NotNull FacetsProvider getFacetsProvider() {
      return myFacetsProvider;
    }

    @Override
    public Library findLibrary(@NotNull String level, @NotNull String libraryName) {
      if (level.equals(LibraryTablesRegistrar.PROJECT_LEVEL)) {
        return myModelsProvider.getLibraryByName(libraryName);
      }
      final LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTableByLevel(level, myProject);
      return table != null ? table.getLibraryByName(libraryName) : null;
    }

    @Override
    public @NotNull ManifestFileProvider getManifestFileProvider() {
      return myManifestFileProvider;
    }
  }

  private class MyModulesProvider implements ModulesProvider {
    @Override
    public Module @NotNull [] getModules() {
      return myModelsProvider.getModules();
    }

    @Override
    public Module getModule(@NotNull String name) {
      return myModelsProvider.findIdeModule(name);
    }

    @Override
    public ModuleRootModel getRootModel(@NotNull Module module) {
      return myModelsProvider.getModifiableRootModel(module);
    }

    @Override
    public @NotNull FacetModel getFacetModel(@NotNull Module module) {
      return myModelsProvider.getModifiableFacetModel(module);
    }
  }

  private class MyFacetsProvider implements FacetsProvider {
    @Override
    public Facet @NotNull [] getAllFacets(Module module) {
      return myModelsProvider.getModifiableFacetModel(module).getAllFacets();
    }

    @Override
    public @NotNull @Unmodifiable <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type) {
      return myModelsProvider.getModifiableFacetModel(module).getFacetsByType(type);
    }

    @Override
    public <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name) {
      return myModelsProvider.getModifiableFacetModel(module).findFacet(type, name);
    }
  }

  private static class DummyArtifactModel implements ModifiableArtifactModel {
    @Override
    public @NotNull ModifiableArtifact addArtifact(@NotNull String name,
                                                   @NotNull ArtifactType artifactType,
                                                   CompositePackagingElement<?> rootElement,
                                                   @Nullable ProjectModelExternalSource externalSource) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeArtifact(@NotNull Artifact artifact) {
    }

    @Override
    public @NotNull ModifiableArtifact getOrCreateModifiableArtifact(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @Nullable Artifact getModifiableCopy(@NotNull Artifact artifact) {
      return null;
    }

    @Override
    public void addListener(@NotNull ArtifactListener listener) {
    }

    @Override
    public void removeListener(@NotNull ArtifactListener listener) {
    }

    @Override
    public boolean isModified() {
      return false;
    }

    @Override
    public void commit() {
    }

    @Override
    public void dispose() {
    }

    @Override
    public Artifact @NotNull [] getArtifacts() {
      return new Artifact[0];
    }

    @Override
    public @Nullable Artifact findArtifact(@NotNull String name) {
      return null;
    }

    @Override
    public @NotNull Artifact getArtifactByOriginal(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Artifact getOriginalArtifact(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException();
    }

    @Override
    public @NotNull Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends Artifact> getAllArtifactsIncludingInvalid() {
      throw new UnsupportedOperationException();
    }
  }
}
