package com.intellij.compiler.artifacts;

import com.intellij.facet.Facet;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.packaging.elements.PackagingElementResolvingContext;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.impl.elements.ManifestFileUtil;
import com.intellij.packaging.ui.ArtifactEditor;
import com.intellij.packaging.ui.ArtifactEditorContext;
import com.intellij.packaging.ui.ManifestFileConfiguration;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author nik
 */
public abstract class ArtifactsTestCase extends IdeaTestCase {
  protected boolean mySetupModule;

  protected ArtifactManager getArtifactManager() {
    return ArtifactManager.getInstance(myProject);
  }

  @Override
  protected void setUpModule() {
    if (mySetupModule) {
      super.setUpModule();
    }
  }

  protected void deleteArtifact(final Artifact artifact) {
    new WriteAction() {
      protected void run(final Result result) {
        final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
        model.removeArtifact(artifact);
        model.commit();
      }
    }.execute();
  }

  protected Artifact rename(Artifact artifact, String newName) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(artifact).setName(newName);
    model.commit();
    return artifact;
  }

  protected Artifact addArtifact(String name) {
    return addArtifact(name, null);
  }

  protected Artifact addArtifact(String name, final CompositePackagingElement<?> root) {
    return addArtifact(name, PlainArtifactType.getInstance(), root);
  }

  protected Artifact addArtifact(final String name, final ArtifactType type, final CompositePackagingElement<?> root) {
    return getArtifactManager().addArtifact(name, type, root);
  }

  protected PackagingElementResolvingContext getContext() {
    return ArtifactManager.getInstance(myProject).getResolvingContext();
  }

  protected class MockPackagingEditorContext implements ArtifactEditorContext {
    private ModifiableArtifactModel myModifiableModel;
    private Map<CompositePackagingElement<?>, ManifestFileConfiguration> myManifestFiles = new HashMap<CompositePackagingElement<?>, ManifestFileConfiguration>();

    @NotNull
    public ModifiableArtifactModel getModifiableArtifactModel() {
      if (myModifiableModel == null) {
        myModifiableModel = ArtifactManager.getInstance(myProject).createModifiableModel();
      }
      return myModifiableModel;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    @NotNull
    public ArtifactModel getArtifactModel() {
      if (myModifiableModel != null) {
        return myModifiableModel;
      }
      return ArtifactManager.getInstance(myProject);
    }

    @NotNull
    public ModulesProvider getModulesProvider() {
      return new DefaultModulesProvider(myProject);
    }

    @NotNull
    public FacetsProvider getFacetsProvider() {
      return DefaultFacetsProvider.INSTANCE;
    }

    public void queueValidation() {
    }

    @NotNull
    public ManifestFileConfiguration getManifestFile(CompositePackagingElement<?> element, ArtifactType artifactType) {
      ManifestFileConfiguration configuration = myManifestFiles.get(element);
      if (configuration == null) {
        configuration = ManifestFileUtil.createManifestFileConfiguration(element, this, PlainArtifactType.getInstance());
        myManifestFiles.put(element, configuration);
      }
      return configuration;
    }

    public boolean isManifestFile(String path) {
      return false;
    }

    public CompositePackagingElement<?> getRootElement(@NotNull Artifact artifact) {
      throw new UnsupportedOperationException("'getRootElement' not implemented in " + getClass().getName());
    }

    public ArtifactEditor getOrCreateEditor(Artifact artifact) {
      throw new UnsupportedOperationException("'getOrCreateEditor' not implemented in " + getClass().getName());
    }

    public ArtifactEditor getThisArtifactEditor() {
      throw new UnsupportedOperationException("'getThisArtifactEditor' not implemented in " + getClass().getName());
    }

    public void selectArtifact(@NotNull Artifact artifact) {
    }

    public void selectFacet(@NotNull Facet<?> facet) {
    }

    public void selectModule(@NotNull Module module) {
    }

    public void selectLibrary(@NotNull Library library) {
    }

    public void editLayout(@NotNull Artifact artifact, Runnable runnable) {
    }

    @NotNull
    public ArtifactType getArtifactType() {
      throw new UnsupportedOperationException("'getArtifactType' not implemented in " + getClass().getName());
    }

    public List<Artifact> chooseArtifacts(List<? extends Artifact> artifacts, String title) {
      throw new UnsupportedOperationException("'chooseArtifacts' not implemented in " + getClass().getName());
    }

    public List<Module> chooseModules(List<Module> modules, String title) {
      throw new UnsupportedOperationException("'chooseModules' not implemented in " + getClass().getName());
    }

    public List<Library> chooseLibraries(List<Library> libraries, String title) {
      throw new UnsupportedOperationException("'chooseLibraries' not implemented in " + getClass().getName());
    }

    public Artifact getArtifact() {
      throw new UnsupportedOperationException("'getArtifact' not implemented in " + getClass().getName());
    }
  }
}
