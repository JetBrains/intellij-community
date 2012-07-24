package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceBase;

/**
 * @author nik
 */
public class JpsArtifactReferenceImpl extends JpsNamedElementReferenceBase<JpsArtifact,JpsArtifactReferenceImpl> implements JpsArtifactReference {
  public JpsArtifactReferenceImpl(@NotNull String artifactName) {
    super(JpsArtifactKind.ARTIFACT_COLLECTION_KIND, artifactName, JpsElementFactory.getInstance().createProjectReference());
  }

  private JpsArtifactReferenceImpl(JpsArtifactReferenceImpl original) {
    super(original);
  }

  @NotNull
  @Override
  public JpsArtifactReferenceImpl createCopy() {
    return new JpsArtifactReferenceImpl(this);
  }

  @NotNull
  @Override
  public String getArtifactName() {
    return myElementName;
  }

  @Override
  public JpsArtifactReferenceImpl asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
