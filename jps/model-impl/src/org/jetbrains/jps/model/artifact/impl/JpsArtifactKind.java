package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.impl.JpsElementCollectionKind;
import org.jetbrains.jps.model.impl.JpsElementKindBase;

/**
* @author nik
*/
public class JpsArtifactKind extends JpsElementKindBase<JpsArtifact> {
  public static final JpsArtifactKind INSTANCE = new JpsArtifactKind();
  public static final JpsElementCollectionKind<JpsArtifact> ARTIFACT_COLLECTION_KIND = new JpsElementCollectionKind<JpsArtifact>(INSTANCE);

  public JpsArtifactKind() {
    super("artifact");
  }
}
