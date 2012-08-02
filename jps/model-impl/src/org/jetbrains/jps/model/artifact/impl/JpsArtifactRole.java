package org.jetbrains.jps.model.artifact.impl;

import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.impl.JpsElementCollectionRole;
import org.jetbrains.jps.model.impl.JpsElementChildRoleBase;

/**
* @author nik
*/
public class JpsArtifactRole extends JpsElementChildRoleBase<JpsArtifact> {
  public static final JpsArtifactRole INSTANCE = new JpsArtifactRole();
  public static final JpsElementCollectionRole<JpsArtifact> ARTIFACT_COLLECTION_ROLE = JpsElementCollectionRole.create(INSTANCE);

  public JpsArtifactRole() {
    super("artifact");
  }
}
