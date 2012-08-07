package org.jetbrains.jps.model.java;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsElementTypeWithDefaultProperties;
import org.jetbrains.jps.model.module.JpsModuleType;

/**
 * @author nik
 */
public class JpsJavaModuleType extends JpsModuleType<JpsDummyElement> implements JpsElementTypeWithDefaultProperties<JpsDummyElement> {
  public static final JpsJavaModuleType INSTANCE = new JpsJavaModuleType();

  @NotNull
  @Override
  public JpsDummyElement createDefaultProperties() {
    return JpsElementFactory.getInstance().createDummyElement();
  }
}
