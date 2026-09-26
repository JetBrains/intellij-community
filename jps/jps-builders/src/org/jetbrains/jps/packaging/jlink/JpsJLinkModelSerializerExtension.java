// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.packaging.jlink;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsModelSerializerExtension;
import org.jetbrains.jps.model.serialization.artifact.ArtifactPropertiesState;
import org.jetbrains.jps.model.serialization.artifact.JpsArtifactPropertiesSerializer;

import java.util.Collections;
import java.util.List;

public final class JpsJLinkModelSerializerExtension extends JpsModelSerializerExtension {

  @Override
  public @NotNull List<? extends JpsArtifactPropertiesSerializer<?>> getArtifactTypePropertiesSerializers() {
    return Collections.singletonList(new JpsJLinkArtifactPropertiesSerializer());
  }

  private static final class JpsJLinkArtifactPropertiesSerializer extends JpsArtifactPropertiesSerializer<JpsJLinkProperties> {

    JpsJLinkArtifactPropertiesSerializer() {
      super("jlink", JpsJLinkArtifactType.INSTANCE);
    }

    @Override
    public JpsJLinkProperties loadProperties(List<ArtifactPropertiesState> stateList) {
      final ArtifactPropertiesState state = findApplicationProperties(stateList);
      if (state != null) {
        final Element options = state.getOptions();
        if (options != null) return new JpsJLinkProperties(XmlSerializer.deserialize(options, JpsJLinkProperties.class));
      }
      return new JpsJLinkProperties();
    }

    private static ArtifactPropertiesState findApplicationProperties(List<ArtifactPropertiesState> stateList) {
      return ContainerUtil.find(stateList, state -> "jlink-properties".equals(state.getId()));
    }
  }
}
