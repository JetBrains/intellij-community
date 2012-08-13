package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceImpl;

/**
 * @author nik
 */
public class JpsArtifactReferenceImpl extends JpsNamedElementReferenceImpl<JpsArtifact,JpsArtifactReferenceImpl> implements JpsArtifactReference {
  public JpsArtifactReferenceImpl(@NotNull String artifactName) {
    super(JpsArtifactRole.ARTIFACT_COLLECTION_ROLE, artifactName, JpsElementFactory.getInstance().createProjectReference());
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
