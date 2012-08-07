package org.jetbrains.jps.model.artifact;

import org.jetbrains.jps.model.JpsDummyElement;

/**
 * @author nik
 */
public class DirectoryArtifactType extends JpsArtifactType<JpsDummyElement> {
  public static final DirectoryArtifactType INSTANCE = new DirectoryArtifactType();
}
