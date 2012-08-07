package org.jetbrains.jps.model.serialization.artifact;

import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.artifact.JpsArtifactType;
import org.jetbrains.jps.model.serialization.JpsElementPropertiesSerializer;

import java.util.List;

/**
 * @author nik
 */
public abstract class JpsArtifactPropertiesSerializer<P extends JpsElement> extends JpsElementPropertiesSerializer<P, JpsArtifactType<P>> {
  public JpsArtifactPropertiesSerializer(String typeId, JpsArtifactType<P> type) {
    super(type, typeId);
  }

  public abstract P loadProperties(List<ArtifactPropertiesState> stateList);

  public abstract void saveProperties(P properties, List<ArtifactPropertiesState> stateList);
}
