package com.intellij.compiler.artifacts;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.packaging.artifacts.*;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;
import com.intellij.packaging.ui.PackagingEditorContext;
import com.intellij.packaging.elements.CompositePackagingElement;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import com.intellij.openapi.roots.ui.configuration.DefaultModulesProvider;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.facet.impl.DefaultFacetsProvider;
import org.jetbrains.annotations.NotNull;

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

  protected void deleteArtifact(Artifact artifact) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.removeArtifact(artifact);
    model.commit();
  }

  protected Artifact rename(Artifact artifact, String newName) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(artifact).setName(newName);
    model.commit();
    return model.getArtifactByOriginal(artifact);
  }

  protected Artifact addArtifact(String name) {
    return addArtifact(name, PlainArtifactType.getInstance(), null);
  }

  protected Artifact addArtifact(String name, final ArtifactType type, CompositePackagingElement<?> root) {
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact artifact = model.addArtifact(name, type);
    if (root != null) {
      artifact.setRootElement(root);
    }
    model.commit();
    return artifact;
  }

  protected class MockPackagingEditorContext implements PackagingEditorContext {
    private ModifiableArtifactModel myModifiableModel;

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
  }
}
