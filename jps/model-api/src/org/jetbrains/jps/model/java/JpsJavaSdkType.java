package org.jetbrains.jps.model.java;

import org.jetbrains.jps.model.DummyJpsElementProperties;
import org.jetbrains.jps.model.library.JpsSdkType;

/**
 * @author nik
 */
public class JpsJavaSdkType extends JpsSdkType<DummyJpsElementProperties>  {
  public static final JpsJavaSdkType INSTANCE = new JpsJavaSdkType();

  @Override
  public DummyJpsElementProperties createDefaultProperties() {
    return DummyJpsElementProperties.INSTANCE;
  }

  @Override
  public DummyJpsElementProperties createCopy(DummyJpsElementProperties properties) {
    return DummyJpsElementProperties.INSTANCE;
  }
}
