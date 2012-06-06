package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.module.JpsModuleType;

/**
 * @author nik
 */
public class JpsJavaModuleType extends JpsModuleType<DummyJpsElementProperties> {
  public static final JpsJavaModuleType INSTANCE = new JpsJavaModuleType();

  @Override
  public DummyJpsElementProperties createDefaultProperties() {
    return DummyJpsElementProperties.INSTANCE;
  }

  @Override
  public DummyJpsElementProperties createCopy(DummyJpsElementProperties properties) {
    return DummyJpsElementProperties.INSTANCE;
  }
}
