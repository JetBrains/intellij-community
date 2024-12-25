// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactReference;
import org.jetbrains.jps.model.impl.JpsNamedElementReferenceImpl;

class JpsArtifactReferenceImpl extends JpsNamedElementReferenceImpl<JpsArtifact,JpsArtifactReferenceImpl> implements JpsArtifactReference {
  JpsArtifactReferenceImpl(@NotNull String artifactName) {
    super(JpsArtifactRole.ARTIFACT_COLLECTION_ROLE, artifactName, JpsElementFactory.getInstance().createProjectReference());
  }

  private JpsArtifactReferenceImpl(JpsArtifactReferenceImpl original) {
    super(original);
  }

  @Override
  public @NotNull JpsArtifactReferenceImpl createCopy() {
    return new JpsArtifactReferenceImpl(this);
  }

  @Override
  public @NotNull String getArtifactName() {
    return myElementName;
  }

  @Override
  public JpsArtifactReferenceImpl asExternal(@NotNull JpsModel model) {
    model.registerExternalReference(this);
    return this;
  }
}
