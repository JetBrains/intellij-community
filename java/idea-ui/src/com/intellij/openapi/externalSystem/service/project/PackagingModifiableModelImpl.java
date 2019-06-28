// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public ModifiableArtifactModel getModifiableArtifactModel() {
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

  @NotNull
  @Override
  public PackagingElementResolvingContext getPackagingElementResolvingContext() {
    if (myPackagingElementResolvingContext == null) {
      myPackagingElementResolvingContext = new MyPackagingElementResolvingContext();
    }
    return myPackagingElementResolvingContext;
  }

  @Override
  public ArtifactExternalDependenciesImporter getArtifactExternalDependenciesImporter() {
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
    @NotNull
    public Project getProject() {
      return myProject;
    }

    @Override
    @NotNull
    public ArtifactModel getArtifactModel() {
      return PackagingModifiableModelImpl.this.getModifiableArtifactModel();
    }

    @Override
    @NotNull
    public ModulesProvider getModulesProvider() {
      return myModulesProvider;
    }

    @Override
    @NotNull
    public FacetsProvider getFacetsProvider() {
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

    @NotNull
    @Override
    public ManifestFileProvider getManifestFileProvider() {
      return myManifestFileProvider;
    }
  }

  private class MyModulesProvider implements ModulesProvider {
    @Override
    @NotNull
    public Module[] getModules() {
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

    @NotNull
    @Override
    public FacetModel getFacetModel(@NotNull Module module) {
      return myModelsProvider.getModifiableFacetModel(module);
    }
  }

  private class MyFacetsProvider implements FacetsProvider {
    @Override
    @NotNull
    public Facet[] getAllFacets(Module module) {
      return myModelsProvider.getModifiableFacetModel(module).getAllFacets();
    }

    @Override
    @NotNull
    public <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type) {
      return myModelsProvider.getModifiableFacetModel(module).getFacetsByType(type);
    }

    @Override
    public <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name) {
      return myModelsProvider.getModifiableFacetModel(module).findFacet(type, name);
    }
  }

  private static class DummyArtifactModel implements ModifiableArtifactModel {
    @NotNull
    @Override
    public ModifiableArtifact addArtifact(@NotNull String name, @NotNull ArtifactType artifactType) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ModifiableArtifact addArtifact(@NotNull String name,
                                          @NotNull ArtifactType artifactType,
                                          CompositePackagingElement<?> rootElement) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public ModifiableArtifact addArtifact(@NotNull String name,
                                          @NotNull ArtifactType artifactType,
                                          CompositePackagingElement<?> rootElement,
                                          @Nullable ProjectModelExternalSource externalSource) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void removeArtifact(@NotNull Artifact artifact) {
    }

    @NotNull
    @Override
    public ModifiableArtifact getOrCreateModifiableArtifact(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException();
    }

    @Nullable
    @Override
    public Artifact getModifiableCopy(Artifact artifact) {
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

    @NotNull
    @Override
    public Artifact[] getArtifacts() {
      return new Artifact[0];
    }

    @Nullable
    @Override
    public Artifact findArtifact(@NotNull String name) {
      return null;
    }

    @NotNull
    @Override
    public Artifact getArtifactByOriginal(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Artifact getOriginalArtifact(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException();
    }

    @NotNull
    @Override
    public Collection<? extends Artifact> getArtifactsByType(@NotNull ArtifactType type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<? extends Artifact> getAllArtifactsIncludingInvalid() {
      throw new UnsupportedOperationException();
    }
  }
}
