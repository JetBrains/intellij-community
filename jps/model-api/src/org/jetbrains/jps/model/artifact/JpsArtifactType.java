package org.jetbrains.jps.model.artifact;

import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.JpsElementType;

/**
 * @author nik
 */
public abstract class JpsArtifactType extends JpsElementType<DummyJpsElementProperties> {
  @Override
  public DummyJpsElementProperties createCopy(DummyJpsElementProperties properties) {
    return  DummyJpsElementProperties.INSTANCE;
  }
}
