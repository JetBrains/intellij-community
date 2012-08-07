package org.jetbrains.jps.model.serialization.artifact;

import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.artifact.JpsArtifactType;

import java.util.List;

/**
 * @author nik
 */
public class JpsArtifactDummyPropertiesSerializer extends JpsArtifactPropertiesSerializer<JpsDummyElement> {
  public JpsArtifactDummyPropertiesSerializer(String typeId, JpsArtifactType<JpsDummyElement> type) {
    super(typeId, type);
  }

  @Override
  public JpsDummyElement loadProperties(List<ArtifactPropertiesState> stateList) {
    return JpsElementFactory.getInstance().createDummyElement();
  }

  @Override
  public void saveProperties(JpsDummyElement properties, List<ArtifactPropertiesState> stateList) {
  }
}
