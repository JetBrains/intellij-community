package com.intellij.compiler.artifacts;

import com.intellij.testFramework.IdeaTestCase;
import com.intellij.packaging.artifacts.ArtifactManager;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.artifacts.ModifiableArtifact;
import com.intellij.packaging.impl.artifacts.PlainArtifactType;

/**
 * @author nik
 */
public abstract class ArtifactsTestCase extends IdeaTestCase {

  protected ArtifactManager getArtifactManager() {
    return ArtifactManager.getInstance(myProject);
  }

  @Override
  protected void setUpModule() {
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
    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    final ModifiableArtifact artifact = model.addArtifact(name, PlainArtifactType.getInstance());
    model.commit();
    return artifact;
  }
}
