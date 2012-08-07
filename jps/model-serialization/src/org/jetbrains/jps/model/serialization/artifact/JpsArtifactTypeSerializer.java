package org.jetbrains.jps.model.serialization.artifact;

import org.jetbrains.jps.model.artifact.JpsArtifactType;

/**
 * @author nik
 */
public class JpsArtifactTypeSerializer {
  private final String myTypeId;
  private final JpsArtifactType myType;

  public JpsArtifactTypeSerializer(String typeId, JpsArtifactType type) {
    myTypeId = typeId;
    myType = type;
  }

  public String getTypeId() {
    return myTypeId;
  }

  public JpsArtifactType getType() {
    return myType;
  }
}
