// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.model.serialization.artifact;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.artifact.JpsArtifactType;

import java.util.List;

@ApiStatus.Internal
public final class JpsArtifactDummyPropertiesSerializer extends JpsArtifactPropertiesSerializer<JpsDummyElement> {
  public JpsArtifactDummyPropertiesSerializer(String typeId, JpsArtifactType<JpsDummyElement> type) {
    super(typeId, type);
  }

  @Override
  public JpsDummyElement loadProperties(List<ArtifactPropertiesState> stateList) {
    return JpsElementFactory.getInstance().createDummyElement();
  }
}
